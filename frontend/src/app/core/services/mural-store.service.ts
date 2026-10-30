import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ComunicadoMuralResponse } from '../models/notificacao.models';
import { getErrorMessage } from '../models/shared.models';
import { AuthService } from './auth.service';
import { ComunicadosService } from './comunicados.service';
import { NotificacoesStore } from './notificacoes-store.service';

/**
 * Estado do mural do usuário.
 *
 * `GET /v1/mural` é chamado uma vez por entrada na aplicação e a lista é distribuída por
 * `apresentacao`: modal ao entrar, faixa no topo e card na página do mural. A ordem devolvida
 * pelo servidor (`fixado DESC, dataInicio DESC`) é preservada.
 */
@Injectable({ providedIn: 'root' })
export class MuralStore {
  private readonly service = inject(ComunicadosService);
  private readonly auth = inject(AuthService);
  private readonly messages = inject(MessageService);
  private readonly notificacoes = inject(NotificacoesStore);

  private readonly _itens = signal<ComunicadoMuralResponse[]>([]);
  private readonly _carregando = signal(false);
  private readonly _carregado = signal(false);
  /** Fechados apenas nesta sessão (o "X"): voltam no próximo carregamento. */
  private readonly _ocultosNaSessao = signal<ReadonlySet<string>>(new Set());

  readonly itens = this._itens.asReadonly();
  readonly carregando = this._carregando.asReadonly();
  readonly carregado = this._carregado.asReadonly();

  private readonly ativos = computed(() => {
    const ocultos = this._ocultosNaSessao();
    return this._itens().filter((item) => !ocultos.has(item.id));
  });

  /** Abrem sobre a aplicação assim que o usuário entra — um por vez, na ordem recebida. */
  readonly modais = computed(() => this.ativos().filter((item) => item.apresentacao === 'MODAL'));
  /** Faixa fixa no topo, não bloqueia o uso. */
  readonly banners = computed(() => this.ativos().filter((item) => item.apresentacao === 'BANNER'));
  readonly totalFixados = computed(() => this._itens().filter((item) => item.fixado).length);
  readonly pendentesConfirmacao = computed(
    () => this._itens().filter((item) => item.exigeConfirmacao && !item.confirmado).length
  );

  private tokenCarregado: string | null = null;

  constructor() {
    effect(() => {
      const token = this.auth.token() ?? null;
      untracked(() => {
        if (token === this.tokenCarregado) return;
        this.tokenCarregado = token;
        this._itens.set([]);
        this._ocultosNaSessao.set(new Set());
        this._carregado.set(false);
        if (token) this.carregar();
      });
    });
  }

  carregar(forcar = false): void {
    if (!this.auth.isAuthenticated()) return;
    if (this._carregando()) return;
    if (this._carregado() && !forcar) return;

    this._carregando.set(true);
    this.service.listarMural().subscribe({
      next: (itens) => {
        this._itens.set(itens ?? []);
        this._carregado.set(true);
        this._carregando.set(false);
      },
      error: () => {
        this._carregando.set(false);
        this._carregado.set(true);
      }
    });
  }

  /** Registra a abertura (modal aberto, banner expandido, card aberto). Não remove do mural. */
  registrarVisualizacao(item: ComunicadoMuralResponse): void {
    if (item.visualizado) return;
    this.atualizarItem(item.id, { visualizado: true });
    this.service.registrarVisualizacao(item.id).subscribe({
      next: () => undefined,
      error: (erro) => this.tratarErro(erro)
    });
  }

  /** Fecha só nesta sessão: volta a aparecer no próximo carregamento do mural. */
  ocultarNaSessao(id: string): void {
    this._ocultosNaSessao.update((atual) => new Set(atual).add(id));
  }

  /** "Não exibir novamente" — some para sempre para este usuário. */
  dispensar(item: ComunicadoMuralResponse): void {
    this.ocultarNaSessao(item.id);
    this.service.dispensar(item.id).subscribe({
      next: () => this.remover(item.id),
      error: (erro) => this.tratarErro(erro)
    });
  }

  /**
   * "Ver depois". Sem `adiarAte` a política é do servidor — não replique nenhum prazo aqui.
   */
  adiar(item: ComunicadoMuralResponse, adiarAte?: Date): void {
    this.ocultarNaSessao(item.id);
    const corpo = adiarAte ? { adiarAte: adiarAte.toISOString() } : undefined;
    this.service.adiar(item.id, corpo).subscribe({
      next: () => undefined,
      error: (erro) => this.tratarErro(erro)
    });
  }

  confirmar(item: ComunicadoMuralResponse): void {
    this.service.confirmar(item.id).subscribe({
      next: () => {
        this.atualizarItem(item.id, { visualizado: true, confirmado: true });
        this.ocultarNaSessao(item.id);
        this.messages.add({
          severity: 'success',
          summary: 'Confirmação registrada',
          detail: 'Obrigado por confirmar a leitura deste comunicado.'
        });
        // O comunicado também pode ter entregue uma notificação no sininho.
        this.notificacoes.carregarResumo();
      },
      error: (erro) => this.tratarErro(erro)
    });
  }

  baixarAnexo(idComunicado: string, idAnexo: string) {
    return this.service.baixarAnexoMural(idComunicado, idAnexo);
  }

  private atualizarItem(id: string, mudanca: Partial<ComunicadoMuralResponse>): void {
    this._itens.update((itens) =>
      itens.map((item) => (item.id === id ? { ...item, ...mudanca } : item))
    );
  }

  private remover(id: string): void {
    this._itens.update((itens) => itens.filter((item) => item.id !== id));
  }

  /**
   * 422 = o comunicado saiu do ar enquanto a tela estava aberta. É tela desatualizada, não erro
   * do usuário: avisa e recarrega o mural.
   */
  private tratarErro(erro: unknown): void {
    if (erro instanceof HttpErrorResponse && erro.status === 422) {
      this.messages.add({
        severity: 'info',
        summary: 'Comunicado indisponível',
        detail: getErrorMessage(erro)
      });
      this.carregar(true);
      return;
    }
    this.messages.add({
      severity: 'error',
      summary: 'Não foi possível concluir',
      detail: getErrorMessage(erro)
    });
  }
}
