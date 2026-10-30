import { InjectionToken } from '@angular/core';
import Dexie, { Table } from 'dexie';
import { SnapshotGravado } from './snapshot';

/**
 * Banco PRÓPRIO da sessão e dos rascunhos, separado do `ces-field-db` (decisão D5).
 *
 * O `ces-field-db` guarda a fila de envio e continua na versão 7: assim um rollback
 * para o build anterior abre o mesmo banco sem `VersionError`, e simplesmente
 * ignora este aqui. Nada neste banco é necessário para enviar dados ao servidor.
 */

export interface RegistroSnapshot {
  chave: 'atual' | 'anterior';
  dados: SnapshotGravado;
}

export interface RascunhoGravado<T = unknown> {
  /** `<tela>:<entidade>`, ex.: `execucao:nova`, `execucao:7f3c…`. */
  chave: string;
  idUsuario: string;
  valor: T;
  /** Incrementa a cada gravação; base da detecção de conflito entre abas. */
  versao: number;
  idAba: string;
  criadoEm: number;
  atualizadoEm: number;
  /** Texto curto para "Continuar lançamento" (ex.: "Nota 7004512 · passo 3 de 4"). */
  titulo?: string;
  /** Rota que reabre este rascunho. */
  rota?: string;
  /** Ids de `anexos` referenciados pelo valor (fotos, assinaturas). */
  anexos: string[];
  checksum: string;
}

export interface AnexoGravado {
  id: string;
  chaveRascunho: string;
  idUsuario: string;
  blob: Blob;
  /**
   * Reserva quando o navegador recusa Blob no IndexedDB (Safari antigo, WebKit para
   * Windows): os bytes são gravados aqui e o `blob` é remontado na leitura.
   */
  bytes?: ArrayBuffer;
  nome: string;
  tipo: string;
  criadoEm: number;
}

/** Evento de diagnóstico, sem dado pessoal (só tipos, contagens, durações, motivos). */
export interface EventoLocal {
  seq?: number;
  tipo: string;
  em: number;
  dados?: Record<string, string | number | boolean>;
  enviado: 0 | 1;
}

export class SessaoDb extends Dexie {
  snapshots!: Table<RegistroSnapshot, string>;
  rascunhos!: Table<RascunhoGravado, string>;
  anexos!: Table<AnexoGravado, string>;
  eventos!: Table<EventoLocal, number>;

  constructor(nome: string) {
    super(nome);
    this.version(1).stores({
      snapshots: 'chave',
      rascunhos: 'chave, idUsuario, atualizadoEm',
      anexos: 'id, chaveRascunho, criadoEm',
      eventos: '++seq, tipo, em, enviado',
    });
  }
}

export const NOME_SESSAO_DB = 'ces-field-sessao';

/** Testes injetam um nome único para isolar cada caso. */
export const SESSAO_DB = new InjectionToken<SessaoDb>('SESSAO_DB', {
  providedIn: 'root',
  factory: () => new SessaoDb(NOME_SESSAO_DB),
});

/** Identificador desta aba/janela, estável enquanto a página viver. */
export const ID_ABA = new InjectionToken<string>('ID_ABA', {
  providedIn: 'root',
  factory: () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `aba-${Date.now()}-${Math.random()}`),
});

/** O IndexedDB recusou por falta de espaço (nome muda entre navegadores e o Dexie embrulha). */
export function ehQuotaCheia(erro: unknown): boolean {
  const e = erro as { name?: string; inner?: { name?: string }; message?: string } | null;
  const nomes = [e?.name, e?.inner?.name];
  return nomes.includes('QuotaExceededError') || /quota/i.test(e?.message ?? '');
}
