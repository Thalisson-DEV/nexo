import { DestroyRef, EffectRef, Injectable, Injector, effect, inject, untracked } from '@angular/core';
import { RascunhoStore } from './rascunho.store';
import { TokenService } from '../auth/token.service';
import { TelemetriaService } from './telemetria.service';

export const DEBOUNCE_RASCUNHO_MS = 300;

export interface OpcoesVinculo<T> {
  /** `<tela>:<entidade>`. Ex.: `execucao:nova`, `execucao:editar:<id>`. */
  chave: string;
  /** Lê o estado do formulário a partir de signals — é rastreado: toda mudança agenda a gravação. */
  ler: () => T;
  /** Reaplica um rascunho salvo nos signals do formulário. */
  aplicar: (valor: T) => void | Promise<void>;
  /** Resumo para "Continuar lançamento". Também rastreado. */
  titulo?: () => string | undefined;
  rota?: () => string | undefined;
  anexos?: () => string[];
  /** Estado inicial "vazio": não vale a pena gravar (evita rascunho fantasma ao só abrir a tela). */
  vazio?: (valor: T) => boolean;
  injector: Injector;
}

export interface VinculoRascunho {
  /** Resolve depois de reaplicar o rascunho (true) ou de não haver nada a reaplicar (false). */
  readonly restaurado: Promise<boolean>;
  /** Grava agora o que estiver pendente. */
  gravarAgora(): Promise<void>;
  /**
   * Lê o formulário AGORA e grava, sem esperar o debounce nem o ciclo de effects.
   * Uso: logo depois de uma foto entrar no formulário (o app pode morrer em seguida).
   */
  gravarEstadoAtual(): Promise<void>;
  /** Envio enfileirado com sucesso: apaga o rascunho e seus arquivos. */
  concluir(): Promise<void>;
  /** Guarda um arquivo do rascunho na hora (antes do debounce). */
  anexar(blob: Blob, nome: string): Promise<string | null>;
}

/**
 * Liga um formulário (feito de signals) ao armazenamento de rascunhos:
 * restaura ao abrir, grava 300 ms depois de cada alteração, grava na hora quando
 * o app vai para segundo plano ou a tela é destruída, e apaga só após o envio.
 *
 * ```ts
 * readonly rascunho = inject(RascunhoService).vincular({
 *   chave: 'execucao:nova', injector: this.injector,
 *   ler: () => ({ passo: this.passo(), nota: this.nota() }),
 *   aplicar: v => { this.passo.set(v.passo); this.nota.set(v.nota); },
 * });
 * ```
 */
@Injectable({ providedIn: 'root' })
export class RascunhoService {
  private readonly store = inject(RascunhoStore);
  private readonly tokens = inject(TokenService);
  private readonly telemetria = inject(TelemetriaService);
  private readonly pendentes = new Set<() => Promise<void>>();
  private ouvindo = false;

  vincular<T>(opts: OpcoesVinculo<T>): VinculoRascunho {
    this.ouvirSaida();
    const idUsuario = () => this.tokens.ownerId() ?? '';
    let versaoBase: number | undefined;
    let timer: ReturnType<typeof setTimeout> | null = null;
    let pendente: { valor: T; titulo?: string; rota?: string; anexos?: string[] } | null = null;
    let ativo = true;
    let efeito: EffectRef | null = null;

    const gravar = async () => {
      if (timer) { clearTimeout(timer); timer = null; }
      const item = pendente;
      if (!item || !idUsuario()) return;
      pendente = null;
      const v = await this.store.salvar(idUsuario(), opts.chave, item.valor, {
        titulo: item.titulo, rota: item.rota, anexos: item.anexos, versaoBase,
      });
      if (v === null) {
        // Armazenamento recusou: mantém em memória para a próxima tentativa.
        pendente ??= item;
      } else {
        versaoBase = v;
      }
    };
    this.pendentes.add(gravar);

    const restaurado = (async () => {
      let aplicou = false;
      try {
        const salvo = idUsuario() ? await this.store.ler<T>(idUsuario(), opts.chave) : null;
        if (salvo && ativo) {
          versaoBase = salvo.versao;
          await opts.aplicar(salvo.valor);
          aplicou = true;
          void this.telemetria.registrar('rascunho.restaurado', { tela: opts.chave.split(':')[0] });
        }
      } catch {
        /* rascunho ilegível: segue com o formulário vazio, sem apagar nada */
      }
      if (!ativo) return aplicou;

      // Só passa a gravar depois de haver conteúdo de verdade (ou de ter restaurado algo).
      let temConteudo = aplicou;
      // Linha de base: o que já está gravado (ou o formulário recém-aberto). O effect
      // compara com ela em vez de pular a 1ª execução — uma alteração feita antes do
      // 1º ciclo de effects não pode se perder.
      let ultimoGravado = untracked(() => JSON.stringify(opts.ler()));
      efeito = effect(() => {
        const valor = opts.ler();
        const titulo = opts.titulo?.();
        const rota = opts.rota?.();
        const anexos = opts.anexos?.();
        untracked(() => {
          const serial = JSON.stringify(valor);
          if (serial === ultimoGravado) return;
          if (!temConteudo && opts.vazio?.(valor)) return;
          temConteudo = true;
          ultimoGravado = serial;
          pendente = { valor: clonar(valor), titulo, rota, anexos };
          if (timer) clearTimeout(timer);
          timer = setTimeout(() => void gravar(), DEBOUNCE_RASCUNHO_MS);
        });
      }, { injector: opts.injector });
      return aplicou;
    })();

    opts.injector.get(DestroyRef).onDestroy(() => {
      ativo = false;
      efeito?.destroy();
      void gravar().finally(() => this.pendentes.delete(gravar));
    });

    return {
      restaurado,
      gravarAgora: gravar,
      gravarEstadoAtual: async () => {
        if (!ativo) return;
        const valor = untracked(() => opts.ler());
        pendente = {
          valor: clonar(valor),
          titulo: untracked(() => opts.titulo?.()),
          rota: untracked(() => opts.rota?.()),
          anexos: untracked(() => opts.anexos?.()),
        };
        await gravar();
      },
      concluir: async () => {
        ativo = false;
        pendente = null;
        if (timer) clearTimeout(timer);
        efeito?.destroy();
        this.pendentes.delete(gravar);
        await this.store.descartar(opts.chave);
      },
      anexar: (blob, nome) => this.store.anexar(idUsuario(), opts.chave, blob, nome),
    };
  }

  /** Grava todos os formulários abertos (app indo para segundo plano). */
  async gravarTodos(): Promise<void> {
    await Promise.all([...this.pendentes].map(g => g()));
  }

  private ouvirSaida(): void {
    if (this.ouvindo || typeof document === 'undefined') return;
    this.ouvindo = true;
    const gravar = () => void this.gravarTodos();
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') gravar(); });
    document.addEventListener('freeze', gravar);
    window.addEventListener('pagehide', gravar);
  }
}

/** Desacopla do signal (o objeto pode continuar sendo mutado pela tela). */
function clonar<T>(valor: T): T {
  return typeof structuredClone === 'function' ? structuredClone(valor) : JSON.parse(JSON.stringify(valor));
}
