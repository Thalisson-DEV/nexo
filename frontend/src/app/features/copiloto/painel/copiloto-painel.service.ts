import { Injectable, effect, inject, signal } from '@angular/core';
import { FocoPainel } from '../../../core/models/assistente.models';
import type { CopilotoDashboardContext } from '../contexto/copiloto-contexto';
import type { FiltroSugerido } from '../contexto/copiloto-filtro';
import { AuthService } from '../../../core/services/auth.service';

/** Pedido para analisar um elemento específico do painel (gráfico, KPI, tabela). */
export interface PedidoFocoCopiloto {
  readonly foco: FocoPainel;
  readonly pergunta?: string;
  readonly sequencia: number;
}

/** Filtro sugerido que o usuário confirmou ("Aplicar filtro"); o dashboard dono da tabela consome. */
export interface PedidoFiltroCopiloto extends FiltroSugerido {
  readonly sequencia: number;
}

const CHAVE_ABERTO = 'ces.copiloto.painel.aberto';
const CHAVE_LARGURA = 'ces.copiloto.painel.largura';
const CHAVE_CONVERSAS = 'ces.copiloto.painel.conversas';

function lerSessao(chave: string): string | null {
  try {
    return typeof sessionStorage === 'undefined' ? null : sessionStorage.getItem(chave);
  } catch {
    return null;
  }
}

function gravarSessao(chave: string, valor: string | null): void {
  try {
    if (typeof sessionStorage === 'undefined') return;
    if (valor === null) sessionStorage.removeItem(chave);
    else sessionStorage.setItem(chave, valor);
  } catch {
    // armazenamento indisponível (modo privado/cota): o painel segue funcionando só em memória
  }
}

/**
 * Estado de UI do Copiloto contextual, compartilhado por todos os dashboards: painel aberto/fechado
 * (sessão), largura (preferência do dispositivo), pedidos de foco em elementos e a conversa ativa de cada
 * dashboard. Guarda apenas ids de conversa — nenhum conteúdo de conversa fica no navegador.
 */
@Injectable({ providedIn: 'root' })
export class CopilotoPainelService {
  private readonly auth = inject(AuthService);
  private sequencia = 0;

  readonly aberto = signal(lerSessao(CHAVE_ABERTO) === '1');
  readonly largura = signal(this.lerLargura());
  readonly pedido = signal<PedidoFocoCopiloto | null>(null);
  /** Contexto do dashboard em tela (registrado pela área do Copiloto) — valida ações `APPLY_FILTER`. */
  readonly contextoAtivo = signal<CopilotoDashboardContext | null>(null);
  readonly pedidoFiltro = signal<PedidoFiltroCopiloto | null>(null);

  constructor() {
    effect(() => gravarSessao(CHAVE_ABERTO, this.aberto() ? '1' : null));
    effect(() => {
      const largura = this.largura();
      try {
        localStorage.setItem(CHAVE_LARGURA, String(largura));
      } catch {
        // preferência apenas em memória
      }
    });
  }

  /** Só quem pode usar o Copiloto vê o painel (o backend continua exigindo a permissão). */
  disponivelParaUsuario(): boolean {
    return (this.auth.currentUser()?.permissoes ?? []).includes('assistente:chat:use');
  }

  abrir(): void {
    this.aberto.set(true);
  }

  fechar(): void {
    this.aberto.set(false);
  }

  alternar(): void {
    this.aberto.update((aberto) => !aberto);
  }

  /** Abre o painel com foco em um elemento do dashboard ("Explique este gráfico"). */
  perguntarSobre(foco: FocoPainel, pergunta?: string): void {
    this.pedido.set({ foco, pergunta, sequencia: ++this.sequencia });
    this.abrir();
  }

  /** Só chamado após o clique do usuário em "Aplicar filtro". */
  aplicarFiltro(filtro: FiltroSugerido): void {
    this.pedidoFiltro.set({ ...filtro, sequencia: ++this.sequencia });
  }

  consumirFiltro(sequencia: number): void {
    if (this.pedidoFiltro()?.sequencia === sequencia) this.pedidoFiltro.set(null);
  }

  consumirPedido(sequencia: number): void {
    if (this.pedido()?.sequencia === sequencia) this.pedido.set(null);
  }

  conversaDoPainel(painelId: string): string | null {
    return this.mapaConversas()[painelId] ?? null;
  }

  lembrarConversa(painelId: string, conversaId: string | null): void {
    const mapa = this.mapaConversas();
    if (conversaId) mapa[painelId] = conversaId;
    else delete mapa[painelId];
    gravarSessao(this.chaveConversas(), JSON.stringify(mapa));
  }

  private mapaConversas(): Record<string, string> {
    try {
      const bruto = lerSessao(this.chaveConversas());
      const mapa = bruto ? (JSON.parse(bruto) as unknown) : {};
      return mapa && typeof mapa === 'object' && !Array.isArray(mapa) ? { ...(mapa as Record<string, string>) } : {};
    } catch {
      return {};
    }
  }

  /** A conversa de cada painel é por usuário: trocar de conta não reaproveita a conversa de outra pessoa. */
  private chaveConversas(): string {
    return `${CHAVE_CONVERSAS}.${this.auth.currentUser()?.id ?? 'anonimo'}`;
  }

  private lerLargura(): number {
    try {
      const valor = Number(localStorage.getItem(CHAVE_LARGURA));
      return Number.isFinite(valor) && valor >= 340 && valor <= 720 ? valor : 420;
    } catch {
      return 420;
    }
  }
}
