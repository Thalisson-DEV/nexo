import { Injectable, inject, signal } from '@angular/core';
import { SESSAO_DB, ID_ABA, RascunhoGravado, AnexoGravado, ehQuotaCheia } from './sessao-db';
import { fnv1a, jsonCanonico } from './snapshot';

export interface RascunhoLido<T> {
  valor: T;
  versao: number;
  atualizadoEm: number;
  anexos: string[];
  titulo?: string;
  rota?: string;
}

export interface ResumoRascunho {
  chave: string;
  titulo?: string;
  rota?: string;
  atualizadoEm: number;
  /** Mais de 7 dias sem mexer (decisão P2): avisar, nunca apagar sozinho. */
  antigo: boolean;
  temAnexos: boolean;
}

export interface MetaRascunho {
  titulo?: string;
  rota?: string;
  anexos?: string[];
  /** Versão que esta aba leu por último; diferente da gravada = outra aba escreveu. */
  versaoBase?: number;
}

export type EstadoArmazenamento = 'ok' | 'cheio' | 'erro';

export const RASCUNHO_ANTIGO_MS = 7 * 24 * 60 * 60 * 1000;
/** Anexo sem rascunho que o referencie, mais velho que isto, é lixo de uma gravação interrompida. */
export const ANEXO_ORFAO_MS = 24 * 60 * 60 * 1000;
/** Sufixo das cópias preservadas quando duas abas editam o mesmo rascunho. */
export const MARCA_CONFLITO = '#conflito-';

/**
 * Rascunhos de formulário e seus arquivos, gravados no aparelho a cada alteração.
 *
 * Regras que os testes fixam:
 * - toda gravação é uma transação: ou o rascunho inteiro entra, ou nada muda;
 * - um rascunho com soma de verificação inválida nunca é aplicado na tela, e também nunca é apagado;
 * - se outra aba gravou o mesmo rascunho desde a última leitura, a versão dela é copiada
 *   para `<chave>#conflito-<instante>` antes de sobrescrever — nenhuma digitação se perde;
 * - armazenamento cheio não lança para a tela: vira `estado() === 'cheio'`.
 */
@Injectable({ providedIn: 'root' })
export class RascunhoStore {
  private readonly db = inject(SESSAO_DB);
  private readonly idAba = inject(ID_ABA);

  readonly estado = signal<EstadoArmazenamento>('ok');
  /** Incrementa quando um conflito entre abas é preservado (a UI pode avisar). */
  readonly conflitos = signal(0);

  async ler<T>(idUsuario: string, chave: string): Promise<RascunhoLido<T> | null> {
    const r = await this.db.rascunhos.get(chave);
    if (!r || r.idUsuario !== idUsuario) return null;
    if (r.checksum !== fnv1a(jsonCanonico(r.valor))) return null;
    return {
      valor: r.valor as T, versao: r.versao, atualizadoEm: r.atualizadoEm,
      anexos: r.anexos ?? [], titulo: r.titulo, rota: r.rota,
    };
  }

  /** Grava agora. Devolve a nova versão, ou `null` se o armazenamento recusou. */
  async salvar<T>(idUsuario: string, chave: string, valor: T, meta: MetaRascunho = {}, agora = Date.now()): Promise<number | null> {
    try {
      const versao = await this.db.transaction('rw', this.db.rascunhos, async () => {
        const atual = await this.db.rascunhos.get(chave);
        const outraAbaEscreveu = !!atual
          && atual.idAba !== this.idAba
          && meta.versaoBase !== undefined
          && atual.versao !== meta.versaoBase;
        if (atual && outraAbaEscreveu) {
          await this.db.rascunhos.put({ ...atual, chave: `${chave}${MARCA_CONFLITO}${atual.atualizadoEm}` });
        }
        const registro: RascunhoGravado<T> = {
          chave,
          idUsuario,
          valor,
          versao: (atual?.versao ?? 0) + 1,
          idAba: this.idAba,
          criadoEm: atual?.criadoEm ?? agora,
          atualizadoEm: agora,
          titulo: meta.titulo ?? atual?.titulo,
          rota: meta.rota ?? atual?.rota,
          anexos: meta.anexos ?? atual?.anexos ?? [],
          checksum: fnv1a(jsonCanonico(valor)),
        };
        await this.db.rascunhos.put(registro as RascunhoGravado);
        return { versao: registro.versao, conflito: outraAbaEscreveu };
      });
      if (versao.conflito) this.conflitos.update(n => n + 1);
      this.estado.set('ok');
      return versao.versao;
    } catch (erro) {
      this.estado.set(ehQuotaCheia(erro) ? 'cheio' : 'erro');
      return null;
    }
  }

  /**
   * Guarda o arquivo IMEDIATAMENTE (antes de qualquer debounce): abrir a câmera pode
   * matar o app logo em seguida. Devolve o id para o formulário referenciar.
   */
  async anexar(idUsuario: string, chaveRascunho: string, blob: Blob, nome: string, agora = Date.now()): Promise<string | null> {
    const id = await this.gravarAnexo(idUsuario, chaveRascunho, blob, nome, agora, false);
    if (id !== null || this.estado() === 'cheio') return id;
    // Navegador recusou o Blob: grava os bytes (ver AnexoGravado.bytes).
    return this.gravarAnexo(idUsuario, chaveRascunho, blob, nome, agora, true);
  }

  private async gravarAnexo(idUsuario: string, chaveRascunho: string, blob: Blob, nome: string, agora: number, comoBytes: boolean): Promise<string | null> {
    const id = crypto.randomUUID();
    const conteudo = comoBytes
      ? { blob: undefined as unknown as Blob, bytes: await blob.arrayBuffer() }
      : { blob };
    try {
      await this.db.transaction('rw', this.db.anexos, this.db.rascunhos, async () => {
        await this.db.anexos.put({ id, chaveRascunho, idUsuario, ...conteudo, nome, tipo: blob.type, criadoEm: agora });
        const r = await this.db.rascunhos.get(chaveRascunho);
        if (r && !r.anexos.includes(id)) {
          await this.db.rascunhos.put({ ...r, anexos: [...r.anexos, id] });
        }
      });
      this.estado.set('ok');
      return id;
    } catch (erro) {
      this.estado.set(ehQuotaCheia(erro) ? 'cheio' : 'erro');
      return null;
    }
  }

  /** Arquivos gravados para um rascunho (inclui os de uma captura interrompida, ainda sem referência). */
  async anexosDoRascunho(idUsuario: string, chaveRascunho: string): Promise<AnexoGravado[]> {
    const todos = await this.db.anexos.where('chaveRascunho').equals(chaveRascunho).toArray();
    return todos.filter(a => a.idUsuario === idUsuario).sort((a, b) => a.criadoEm - b.criadoEm).map(remontar);
  }

  async lerAnexo(id: string): Promise<AnexoGravado | undefined> {
    const a = await this.db.anexos.get(id);
    return a ? remontar(a) : undefined;
  }

  async removerAnexo(id: string): Promise<void> {
    await this.db.transaction('rw', this.db.anexos, this.db.rascunhos, async () => {
      const a = await this.db.anexos.get(id);
      await this.db.anexos.delete(id);
      if (!a) return;
      const r = await this.db.rascunhos.get(a.chaveRascunho);
      if (r) await this.db.rascunhos.put({ ...r, anexos: r.anexos.filter(x => x !== id) });
    });
  }

  /** Remove o rascunho e seus arquivos numa transação (uso: depois de enfileirar o envio). */
  async descartar(chave: string): Promise<void> {
    await this.db.transaction('rw', this.db.rascunhos, this.db.anexos, async () => {
      await this.db.rascunhos.delete(chave);
      await this.db.anexos.where('chaveRascunho').equals(chave).delete();
    });
  }

  async listar(idUsuario: string, agora = Date.now()): Promise<ResumoRascunho[]> {
    const todos = await this.db.rascunhos.where('idUsuario').equals(idUsuario).toArray();
    return todos
      .sort((a, b) => b.atualizadoEm - a.atualizadoEm)
      .map(r => ({
        chave: r.chave,
        titulo: r.titulo,
        rota: r.rota,
        atualizadoEm: r.atualizadoEm,
        antigo: agora - r.atualizadoEm > RASCUNHO_ANTIGO_MS,
        temAnexos: (r.anexos?.length ?? 0) > 0,
      }));
  }

  /**
   * Limpeza documentada (DECISOES P2): apaga só anexos órfãos com mais de 24 h —
   * sobras de uma gravação interrompida entre `anexar` e o `salvar` seguinte.
   * Rascunhos nunca são apagados aqui, por mais antigos que sejam.
   */
  async limparOrfaos(agora = Date.now()): Promise<number> {
    return this.db.transaction('rw', this.db.anexos, this.db.rascunhos, async () => {
      const referenciados = new Set<string>();
      const chaves = new Set<string>();
      await this.db.rascunhos.each(r => {
        chaves.add(r.chave);
        r.anexos?.forEach(id => referenciados.add(id));
      });
      const orfaos = await this.db.anexos
        .filter(a => !referenciados.has(a.id) && !chaves.has(a.chaveRascunho) && agora - a.criadoEm > ANEXO_ORFAO_MS)
        .primaryKeys();
      await this.db.anexos.bulkDelete(orfaos);
      return orfaos.length;
    });
  }

  /** Sair da conta com confirmação explícita do usuário: apaga rascunhos e arquivos dela. */
  async limparConta(idUsuario: string): Promise<void> {
    await this.db.transaction('rw', this.db.rascunhos, this.db.anexos, async () => {
      await this.db.rascunhos.where('idUsuario').equals(idUsuario).delete();
      await this.db.anexos.filter(a => a.idUsuario === idUsuario).delete();
    });
  }
}

/** Anexo gravado como bytes volta como Blob para quem lê. */
function remontar(a: AnexoGravado): AnexoGravado {
  if (a.blob instanceof Blob || !a.bytes) return a;
  return { ...a, blob: new Blob([a.bytes], { type: a.tipo }) };
}
