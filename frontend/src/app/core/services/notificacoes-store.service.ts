import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { Client, IFrame, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../../environments/environment';
import {
  CategoriaNotificacao,
  NotificacaoResponse,
  ResumoNotificacoesResponse
} from '../models/notificacao.models';
import { AuthService } from './auth.service';
import { NotificacoesService } from './notificacoes.service';

/** Quantos itens o painel do sininho mantém em memória. */
const LIMITE_RECENTES = 12;
const SOCKJS_TIMEOUT_MS = 10_000;

/**
 * Estado da caixa de entrada, alimentado por REST e — quando dá — por push STOMP.
 *
 * O broker é o simples, em memória: com mais de uma instância cada nó só alcança quem está
 * conectado nele, e uma falha de push é engolida no backend. O push é conveniência; o estado
 * real está sempre no banco. Por isso o contador é reescrito pelo `GET /resumo` a cada
 * (re)conexão e a cada retorno de aba em background, nunca derivado só do acumulado de STOMP.
 */
@Injectable({ providedIn: 'root' })
export class NotificacoesStore {
  private readonly service = inject(NotificacoesService);
  private readonly auth = inject(AuthService);

  private readonly _naoLidas = signal(0);
  private readonly _naoLidasPorCategoria = signal<Partial<Record<CategoriaNotificacao, number>>>(
    {}
  );
  private readonly _recentes = signal<NotificacaoResponse[]>([]);
  private readonly _carregando = signal(false);
  private readonly _conectado = signal(false);

  readonly naoLidas = this._naoLidas.asReadonly();
  readonly naoLidasPorCategoria = this._naoLidasPorCategoria.asReadonly();
  readonly recentes = this._recentes.asReadonly();
  readonly carregando = this._carregando.asReadonly();
  readonly conectado = this._conectado.asReadonly();

  readonly temNaoLidas = computed(() => this._naoLidas() > 0);
  readonly badge = computed(() => (this._naoLidas() > 99 ? '99+' : String(this._naoLidas())));

  private cliente: Client | null = null;
  private tokenConectado: string | null = null;
  /** Distingue a primeira conexão (já carregada por REST) de uma reconexão. */
  private jaConectou = false;

  constructor() {
    // O token muda no login, no logout e na renovação. Não existe refresh de credencial dentro
    // da sessão STOMP: para valer o token novo é preciso reconectar.
    effect(() => {
      const token = this.auth.token() ?? null;
      untracked(() => this.sincronizarConexao(token));
    });

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible' && this.auth.isAuthenticated()) {
        this.carregarResumo();
      }
    });
  }

  // -------------------------------------------------------------------------
  // Carregamento
  // -------------------------------------------------------------------------

  /** Rede de segurança oficial do desenho: sobrescreve o contador com o estado do banco. */
  carregarResumo(): void {
    if (!this.auth.isAuthenticated()) return;
    this.service.resumo().subscribe({
      next: (resumo) => this.aplicarResumo(resumo),
      error: () => undefined
    });
  }

  carregarRecentes(): void {
    if (!this.auth.isAuthenticated()) return;
    this._carregando.set(true);
    this.service
      .listar({ page: 0, size: LIMITE_RECENTES, sort: 'dataCriacao,desc' })
      .subscribe({
        next: (pagina) => {
          this._recentes.set(pagina.content);
          this._carregando.set(false);
        },
        error: () => this._carregando.set(false)
      });
  }

  atualizar(): void {
    this.carregarResumo();
    this.carregarRecentes();
  }

  aplicarResumo(resumo: ResumoNotificacoesResponse): void {
    this._naoLidas.set(resumo.naoLidas ?? 0);
    this._naoLidasPorCategoria.set(resumo.naoLidasPorCategoria ?? {});
  }

  // -------------------------------------------------------------------------
  // Ações — otimistas, com rollback quando o servidor recusa
  // -------------------------------------------------------------------------

  marcarLida(notificacao: NotificacaoResponse): void {
    if (notificacao.lida) return;
    this.trocarLeituraLocal(notificacao.id, true);

    this.service.marcarLida(notificacao.id).subscribe({
      next: (atualizada) => this.substituir(atualizada),
      error: (erro) => {
        this.trocarLeituraLocal(notificacao.id, false);
        this.tratarErro(erro);
      }
    });
  }

  marcarNaoLida(notificacao: NotificacaoResponse): void {
    if (!notificacao.lida) return;
    this.trocarLeituraLocal(notificacao.id, false);

    this.service.marcarNaoLida(notificacao.id).subscribe({
      next: (atualizada) => this.substituir(atualizada),
      error: (erro) => {
        this.trocarLeituraLocal(notificacao.id, true);
        this.tratarErro(erro);
      }
    });
  }

  /** A resposta já traz o resumo atualizado — não faça uma segunda chamada ao `/resumo`. */
  marcarTodasLidas(): void {
    this.service.marcarTodasLidas().subscribe({
      next: (resumo) => {
        this.aplicarResumo(resumo);
        this._recentes.update((itens) =>
          itens.map((item) => (item.lida ? item : { ...item, lida: true }))
        );
      },
      error: (erro) => this.tratarErro(erro)
    });
  }

  arquivar(id: string): void {
    const anterior = this._recentes();
    this._recentes.update((itens) => itens.filter((item) => item.id !== id));

    this.service.arquivar(id).subscribe({
      next: () => this.atualizar(),
      error: (erro) => {
        this._recentes.set(anterior);
        this.tratarErro(erro);
      }
    });
  }

  arquivarLidas(): void {
    this.service.arquivarLidas().subscribe({
      next: (resumo) => {
        this.aplicarResumo(resumo);
        this.carregarRecentes();
      },
      error: (erro) => this.tratarErro(erro)
    });
  }

  // -------------------------------------------------------------------------
  // Conexão STOMP
  // -------------------------------------------------------------------------

  private sincronizarConexao(token: string | null): void {
    if (this.tokenConectado === token) return;
    this.tokenConectado = token;
    this.desconectar();

    if (!token) {
      this._naoLidas.set(0);
      this._naoLidasPorCategoria.set({});
      this._recentes.set([]);
      return;
    }

    // O handshake do SockJS não carrega o header Authorization; a identidade é estabelecida no
    // frame CONNECT. Sem token a sessão conecta anônima e nunca recebe a fila privada.
    // O SockJS estima o prazo do WebSocket pelo RTT do /info; do Brasil até o servidor esse prazo
    // estourava e a sessão descia para long-polling (uma requisição de 25 s atrás da outra).
    const cliente = new Client({
      webSocketFactory: () => new SockJS(`${environment.apiUrl}/ws`, undefined, { timeout: SOCKJS_TIMEOUT_MS }),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000
    });

    cliente.onConnect = () => {
      this._conectado.set(true);
      // Rede de segurança da (re)conexão. Na primeira, a lista já veio do REST logo abaixo;
      // numa reconexão pode ter passado notificação enquanto a sessão esteve fora do ar.
      this.carregarResumo();
      if (this.jaConectou) this.carregarRecentes();
      this.jaConectou = true;

      cliente.subscribe('/user/queue/notificacoes', (mensagem: IMessage) => {
        try {
          this.receber(JSON.parse(mensagem.body) as NotificacaoResponse);
        } catch {
          // Payload inesperado não pode derrubar a assinatura; o /resumo corrige o contador.
          this.carregarResumo();
        }
      });
    };

    cliente.onWebSocketClose = () => this._conectado.set(false);
    cliente.onStompError = (frame: IFrame) => {
      this._conectado.set(false);
      console.error('Erro STOMP de notificações:', frame.headers['message'], frame.body);
    };

    this.cliente = cliente;
    cliente.activate();

    // O push pode nunca chegar (broker em memória, várias instâncias). Carrega já pelo REST.
    this.atualizar();
  }

  private desconectar(): void {
    this._conectado.set(false);
    this.jaConectou = false;
    if (!this.cliente) return;
    const anterior = this.cliente;
    this.cliente = null;
    anterior.deactivate().catch(() => undefined);
  }

  private receber(notificacao: NotificacaoResponse): void {
    this._recentes.update((itens) => {
      const semDuplicata = itens.filter((item) => item.id !== notificacao.id);
      return [notificacao, ...semDuplicata].slice(0, LIMITE_RECENTES);
    });

    if (notificacao.lida) return;
    this._naoLidas.update((total) => total + 1);
    this._naoLidasPorCategoria.update((mapa) => ({
      ...mapa,
      [notificacao.categoria]: (mapa[notificacao.categoria] ?? 0) + 1
    }));
  }

  // -------------------------------------------------------------------------
  // Apoio
  // -------------------------------------------------------------------------

  private substituir(notificacao: NotificacaoResponse): void {
    this._recentes.update((itens) =>
      itens.map((item) => (item.id === notificacao.id ? notificacao : item))
    );
  }

  private trocarLeituraLocal(id: string, lida: boolean): void {
    const alvo = this._recentes().find((item) => item.id === id);
    this._recentes.update((itens) =>
      itens.map((item) => (item.id === id ? { ...item, lida } : item))
    );
    this._naoLidas.update((total) => Math.max(0, total + (lida ? -1 : 1)));

    const categoria = alvo?.categoria;
    if (!categoria) return;
    this._naoLidasPorCategoria.update((mapa) => ({
      ...mapa,
      [categoria]: Math.max(0, (mapa[categoria] ?? 0) + (lida ? -1 : 1))
    }));
  }

  /** 404 aqui significa "sumiu" (inclusive notificação de outro usuário): recarregue. */
  private tratarErro(erro: unknown): void {
    if (erro instanceof HttpErrorResponse && erro.status === 404) {
      this.atualizar();
    }
  }
}
