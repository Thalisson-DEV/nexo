import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CesAsyncStateComponent, CesConfirmationService, CesIconButtonComponent, CesTone, NotificationService } from '@ces/ui';
import { catchError, forkJoin, map, Observable, of, switchMap, tap } from 'rxjs';

import { SolicitacaoStatus, SolicitacaoSummaryResponse } from '../../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { SolicitacaoMaterialSummaryResponse } from '../../../../core/models/solicitacao-material-tecnica.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { isoCivil, saqueAtrasado, statusComercial } from '../solicitacao-comercial-status';
import { AcaoCardComercial, SolicitacaoComercialKanbanCardComponent } from './solicitacao-comercial-kanban-card.component';

export type AgrupamentoKanban = 'situacao' | 'pendencia' | 'prazo';
export type ColunaKanbanComercial = 'APROVACAO' | 'ENTREGA' | 'ENCERRADAS';
type Card = SolicitacaoMaterialSummaryResponse;

interface ColunaDef {
  id: ColunaKanbanComercial;
  titulo: string;
  subtitulo: string;
  icon: string;
  tone: CesTone;
  statuses: SolicitacaoStatus[];
  vazio: { icon: string; titulo: string; texto: string };
}

/** Colunas pela próxima ação do almoxarifado: aprovar, entregar ou nada (encerradas). */
const COLUNAS: readonly ColunaDef[] = [
  {
    id: 'APROVACAO',
    titulo: 'Aprovação',
    subtitulo: 'Aguardam a sua decisão',
    icon: 'pi pi-clock',
    tone: 'warning',
    statuses: ['PENDENTE'],
    vazio: { icon: 'pi pi-check-circle', titulo: 'Nada para aprovar', texto: 'Todas as solicitações deste recorte já foram analisadas.' }
  },
  {
    id: 'ENTREGA',
    titulo: 'Entrega',
    subtitulo: 'Aprovadas, esperando a retirada',
    icon: 'pi pi-truck',
    tone: 'info',
    statuses: ['APROVADO', 'ENTREGUE_PARCIAL'],
    vazio: { icon: 'pi pi-box', titulo: 'Nenhuma retirada pendente', texto: 'Quando uma solicitação for aprovada, ela aparece aqui para a entrega.' }
  },
  {
    id: 'ENCERRADAS',
    titulo: 'Encerradas',
    subtitulo: 'Entregues, rejeitadas ou estornadas',
    icon: 'pi pi-inbox',
    tone: 'neutral',
    statuses: ['ENTREGUE', 'REJEITADO', 'ESTORNADO'],
    vazio: { icon: 'pi pi-inbox', titulo: 'Nada encerrado ainda', texto: 'As solicitações concluídas deste recorte aparecem aqui.' }
  }
];

const SITUACAO: Partial<Record<SolicitacaoStatus, { label: string; tone: CesTone }>> = {
  PENDENTE: { label: 'Aguardando aprovação', tone: 'warning' },
  APROVADO: { label: 'Aguardando retirada', tone: 'info' },
  ENTREGUE_PARCIAL: { label: 'Entregue em parte', tone: 'warning' },
  ENTREGUE: { label: 'Entregues', tone: 'success' },
  REJEITADO: { label: 'Rejeitadas', tone: 'danger' },
  ESTORNADO: { label: 'Estornadas', tone: 'neutral' }
};

/** Em aberto o quadro traz a fila inteira (até o limite); encerradas, só as mais recentes. */
export const LIMITE_ABERTAS = 200;
export const LIMITE_ENCERRADAS = 15;
const ABERTOS: readonly SolicitacaoStatus[] = ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL'];
const CHAVE_RECOLHIDOS = 'ces_kanban_comercial_grupos_recolhidos';
const CHAVE_AGRUPAMENTO = 'ces_kanban_comercial_agrupamento';

interface FaixaStatus {
  status: SolicitacaoStatus;
  itens: Card[];
  total: number;
}

export interface GrupoKanban {
  id: string;
  label: string;
  tone: CesTone;
  itens: Card[];
  total: number;
  /** Status da faixa, quando o grupo é por situação (para "ver todas na tabela"). */
  status?: SolicitacaoStatus;
}

interface ColunaVisivel {
  def: ColunaDef;
  grupos: GrupoKanban[];
  total: number;
  atrasadas: number;
  pendencias: number;
}

@Component({
  selector: 'app-solicitacoes-comerciais-kanban',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesAsyncStateComponent, CesIconButtonComponent, SolicitacaoComercialKanbanCardComponent],
  styleUrl: './solicitacoes-comerciais-kanban.component.css',
  template: `
    <div class="kb-bar">
      <div class="kb-agrupar">
        <span id="kb-agrupar-rotulo">Agrupar por</span>
        <div class="kb-seg" role="group" aria-labelledby="kb-agrupar-rotulo">
          <button type="button" [attr.aria-pressed]="agrupamento() === 'situacao'" (click)="agrupar('situacao')"><i class="pi pi-sitemap" aria-hidden="true"></i>Situação</button>
          <button type="button" [attr.aria-pressed]="agrupamento() === 'pendencia'" (click)="agrupar('pendencia')"><i class="pi pi-flag" aria-hidden="true"></i>Pendência</button>
          <button type="button" [attr.aria-pressed]="agrupamento() === 'prazo'" (click)="agrupar('prazo')"><i class="pi pi-calendar" aria-hidden="true"></i>Prazo de saque</button>
        </div>
      </div>
      <span class="kb-dica"><i class="pi pi-arrows-alt" aria-hidden="true"></i>Arraste um cartão para aprovar, entregar ou rejeitar</span>
    </div>

    <nav class="kb-atalhos" aria-label="Ir para a coluna">
      @for (col of colunas(); track col.def.id) {
        <button type="button" [attr.data-tone]="col.def.tone" [attr.aria-current]="colunaVisivel() === $index ? 'true' : null" (click)="irParaColuna(col.def.id)">
          <span class="kb-atalhos__dot" aria-hidden="true"></span>{{ col.def.titulo }}<strong>{{ col.total }}</strong>
        </button>
      }
    </nav>

    <p class="kb-sr" aria-live="polite">{{ anuncio() }}</p>

    @if (erro()) {
      <ces-async-state type="error" title="Não foi possível carregar o quadro" [description]="erro()!" actionLabel="Tentar novamente" (actionPressed)="recarregar()" />
    } @else if (!faixas()) {
      <div class="kb-board" aria-busy="true" aria-label="Carregando o quadro">
        @for (c of [1, 2, 3]; track c) {
          <div class="kb-col kb-col--skeleton">
            <span class="kb-sk kb-sk--head"></span>
            <span class="kb-sk kb-sk--bar"></span>
            <span class="kb-sk kb-sk--card"></span>
            <span class="kb-sk kb-sk--card"></span>
          </div>
        }
      </div>
    } @else {
      <div class="kb-board" [class.kb-board--arrastando]="!!arrastando()" [class.kb-board--atualizando]="carregando()" role="region" aria-label="Quadro de solicitações comerciais" (scroll)="aoRolarQuadro($event)">
        @for (col of colunas(); track col.def.id) {
          @let acao = acaoDeSoltar(col.def.id);
          <section
            class="kb-col"
            [id]="'kb-col-' + col.def.id"
            [attr.data-tone]="col.def.tone"
            [class.kb-col--alvo]="!!acao"
            [class.kb-col--sobre]="!!acao && sobre() === col.def.id"
            [class.kb-col--inerte]="!!arrastando() && !acao && arrastando()!.de !== col.def.id"
            [attr.aria-labelledby]="'kb-titulo-' + col.def.id"
            (dragover)="aoPassar($event, col.def.id)"
            (dragleave)="aoSair($event, col.def.id)"
            (drop)="aoSoltar($event, col.def.id)"
          >
            <header class="kb-col__head">
              <span class="kb-col__icone" aria-hidden="true"><i [class]="col.def.icon"></i></span>
              <div class="kb-col__titulos">
                <h3 [id]="'kb-titulo-' + col.def.id">{{ col.def.titulo }} <span class="kb-col__total">{{ col.total }}</span></h3>
                <p>{{ col.def.subtitulo }}</p>
              </div>
              @if (copiloto()) {
              <ces-icon-button icon="pi pi-sparkles" [label]="'Analisar a etapa ' + col.def.titulo + ' com o Copiloto'" tooltip="Analisar com o Copiloto" size="icon-sm" (pressed)="analisar(col.def)" />
              }
            </header>

            @if (col.atrasadas || col.pendencias) {
              <div class="kb-col__alertas">
                @if (col.atrasadas) {
                  <span class="kb-alerta kb-alerta--perigo"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i>{{ col.atrasadas }} {{ col.atrasadas === 1 ? 'atrasada' : 'atrasadas' }}</span>
                }
                @if (col.pendencias) {
                  <span class="kb-alerta kb-alerta--aviso"><i class="pi pi-flag" aria-hidden="true"></i>{{ col.pendencias }} com pendência</span>
                }
              </div>
            }

            @if (acao) {
              <div class="kb-drop" [attr.data-tone]="acao.tone" aria-hidden="true">
                <span class="kb-drop__icone"><i [class]="acao.icon"></i></span>
                {{ acao.texto }}
              </div>
            }

            <div class="kb-col__corpo">

              @if (col.total === 0) {
                <div class="kb-vazio">
                  <span class="kb-vazio__icone" aria-hidden="true"><i [class]="col.def.vazio.icon"></i></span>
                  <strong>{{ col.def.vazio.titulo }}</strong>
                  <span>{{ col.def.vazio.texto }}</span>
                </div>
              } @else {
                @for (g of col.grupos; track g.id) {
                  @let fechado = estaRecolhido(g);
                  <div class="kb-grupo" [attr.data-tone]="g.tone" [class.kb-grupo--fechado]="fechado" [class.kb-grupo--vazio]="g.total === 0">
                    <button type="button" class="kb-grupo__head" [attr.aria-expanded]="!fechado" [disabled]="g.total === 0" (click)="alternarGrupo(g)">
                      <i class="pi pi-chevron-down kb-grupo__seta" aria-hidden="true"></i>
                      <span class="kb-grupo__label">{{ g.label }}</span>
                      <span class="kb-grupo__count">{{ g.total }}</span>
                    </button>
                    @if (g.total > 0) {
                      <div class="kb-grupo__corpo" [attr.inert]="fechado ? '' : null">
                        <div class="kb-grupo__lista">
                          @for (item of g.itens; track item.id; let i = $index) {
                            <app-solicitacao-comercial-kanban-card
                              [style.--i]="i < 12 ? i : 12"
                              [item]="item"
                              [arrastando]="arrastando()?.item?.id === item.id"
                              [arrastavel]="arrastavel"
                              (cardClicked)="abrir.emit({ item: $event })"
                              (dragStarted)="iniciarArraste($event, col.def.id)"
                              (dragEnded)="encerrarArraste()"
                              (actionTriggered)="aoAgir($event.item, $event.action)"
                            />
                          }
                          @if (g.total > g.itens.length) {
                            <button type="button" class="kb-mais" (click)="verNaTabela.emit(g.status ? [g.status] : col.def.statuses)">
                              Ver todas as {{ g.total }} na tabela <i class="pi pi-arrow-right" aria-hidden="true"></i>
                            </button>
                          }
                        </div>
                      </div>
                    }
                  </div>
                }
              }
            </div>
          </section>
        }
      </div>
    }
  `
})
export class SolicitacoesComerciaisKanbanComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly host = inject(ElementRef<HTMLElement>);

  /** Recorte da tela no formato do backend (inclui `statuses` dos atalhos). */
  readonly filtros = input<Record<string, unknown>>({});
  /** Abre o detalhe; `aba` direciona para a entrega. */
  readonly abrir = output<{ item: Card | SolicitacaoSummaryResponse; aba?: 'entrega' }>();
  readonly rejeitar = output<Card>();
  /** "Ver todas": a tela troca para a tabela com esses status. */
  readonly verNaTabela = output<SolicitacaoStatus[]>();
  /** Uma ação mudou dados (a tela atualiza as contagens). */
  readonly alterado = output<void>();
  /** "Analisar" de uma coluna: a tela abre o Copiloto ao lado. */
  readonly analisarEtapa = output<{ id: string; titulo: string; pergunta: string }>({ alias: 'analisar' });
  /** Esconde o botão de análise quando o usuário não pode usar o Copiloto. */
  readonly copiloto = input(true);

  protected readonly agrupamento = signal<AgrupamentoKanban>(lerAgrupamento());
  private readonly recolhidos = signal<Record<string, boolean>>(lerRecolhidos());
  private readonly versao = signal(0);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly faixas = signal<FaixaStatus[] | null>(null);
  protected readonly anuncio = signal('');

  /** Coluna em foco no celular (uma por vez, com rolagem lateral). */
  protected readonly colunaVisivel = signal(0);
  /** Arrastar só com mouse/caneta; no toque, os botões do cartão fazem as mesmas ações sem brigar com a rolagem. */
  protected readonly arrastavel = typeof matchMedia === 'function' ? matchMedia('(pointer: fine)').matches : true;
  protected readonly arrastando = signal<{ item: Card; de: ColunaKanbanComercial } | null>(null);
  protected readonly sobre = signal<ColunaKanbanComercial | null>(null);

  private readonly consulta = computed(() => ({ filtros: this.filtros(), versao: this.versao() }));

  protected readonly colunas = computed<ColunaVisivel[]>(() => {
    const agrupamento = this.agrupamento();
    // Parcial com saldo encerrado não tem mais o que entregar: sai de Entrega e vai para Encerradas.
    const brutas = this.faixas() ?? [];
    const parcial = brutas.find((f) => f.status === 'ENTREGUE_PARCIAL');
    const saldoEncerrado = parcial?.itens.filter((i) => i.encerrada) ?? [];
    const faixas = brutas.map((f) =>
      f === parcial && saldoEncerrado.length ? { ...f, itens: f.itens.filter((i) => !i.encerrada), total: Math.max(0, f.total - saldoEncerrado.length) } : f
    );
    return COLUNAS.map((def) => {
      const minhas = def.statuses.map((s) => faixas.find((f) => f.status === s) ?? { status: s, itens: [], total: 0 });
      const itens = minhas.flatMap((f) => f.itens);
      let grupos = agrupamento === 'situacao' || def.id === 'ENCERRADAS' ? porSituacao(minhas) : agrupamento === 'pendencia' ? porPendencia(itens) : porPrazo(itens);
      const extra = def.id === 'ENCERRADAS' ? saldoEncerrado.length : 0;
      if (extra) {
        grupos = [grupos[0], { id: 'PARCIAL_ENCERRADA', label: 'Entregue em parte · saldo encerrado', tone: 'neutral', itens: saldoEncerrado, total: extra }, ...grupos.slice(1)];
      }
      return {
        def,
        grupos,
        total: minhas.reduce((acc, f) => acc + f.total, 0) + extra,
        atrasadas: def.id === 'ENCERRADAS' ? 0 : itens.filter((s) => saqueAtrasado(s)).length,
        pendencias: def.id === 'ENCERRADAS' ? 0 : itens.filter((s) => s.possuiPendencia).length
      };
    });
  });

  constructor() {
    toObservable(this.consulta)
      .pipe(
        tap(() => {
          this.carregando.set(true);
          this.erro.set(null);
        }),
        switchMap(({ filtros }) =>
          this.buscar(filtros).pipe(
            catchError((err) => {
              this.erro.set(getErrorMessage(err));
              return of(null);
            })
          )
        ),
        takeUntilDestroyed()
      )
      .subscribe((faixas) => {
        this.carregando.set(false);
        if (faixas) this.faixas.set(faixas);
      });
  }

  recarregar(): void {
    this.versao.update((v) => v + 1);
  }

  /** Uma consulta por status: totais exatos por grupo e respeito aos atalhos de status da tela. */
  private buscar(filtros: Record<string, unknown>): Observable<FaixaStatus[]> {
    const { statuses, ...resto } = filtros as { statuses?: SolicitacaoStatus[] };
    const pedidos = COLUNAS.flatMap((c) => c.statuses).filter((s) => !statuses?.length || statuses.includes(s));
    if (!pedidos.length) return of([]);
    return forkJoin(
      pedidos.map((status) => {
        const aberto = ABERTOS.includes(status);
        return this.almoxarifado
          .listarSolicitacoes({
            ...resto,
            statuses: [status],
            page: 0,
            size: aberto ? LIMITE_ABERTAS : LIMITE_ENCERRADAS,
            sort: aberto ? 'dataPrevistaRetirada,asc' : 'dataSolicitacao,desc'
          })
          .pipe(map((r) => ({ status, itens: (r.content ?? []) as unknown as Card[], total: r.page?.totalElements ?? r.content?.length ?? 0 })));
      })
    );
  }

  protected agrupar(valor: AgrupamentoKanban): void {
    this.agrupamento.set(valor);
    salvar(CHAVE_AGRUPAMENTO, valor);
  }

  protected estaRecolhido(g: GrupoKanban): boolean {
    return g.total === 0 || (this.recolhidos()[`${this.agrupamento()}:${g.id}`] ?? false);
  }

  protected alternarGrupo(g: GrupoKanban): void {
    const chave = `${this.agrupamento()}:${g.id}`;
    this.recolhidos.update((r) => ({ ...r, [chave]: !this.estaRecolhido(g) }));
    salvar(CHAVE_RECOLHIDOS, JSON.stringify(this.recolhidos()));
  }

  protected aoRolarQuadro(evento: Event): void {
    const quadro = evento.target as HTMLElement;
    const primeira = quadro.querySelector<HTMLElement>('.kb-col');
    if (!primeira) return;
    const passo = primeira.offsetWidth + parseFloat(getComputedStyle(quadro).columnGap || '0');
    this.colunaVisivel.set(Math.max(0, Math.min(COLUNAS.length - 1, Math.round(quadro.scrollLeft / passo))));
  }

  protected irParaColuna(id: ColunaKanbanComercial): void {
    const alvo = (this.host.nativeElement as HTMLElement).querySelector<HTMLElement>(`#kb-col-${id}`);
    alvo?.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', inline: 'start', block: 'nearest' });
  }

  protected analisar(def: ColunaDef): void {
    this.analisarEtapa.emit({
      id: def.id,
      titulo: def.titulo,
      pergunta: `Analise as solicitações comerciais na etapa "${def.titulo}" deste recorte, apontando gargalos, atrasos e o que fazer primeiro.`
    });
  }

  // --- Arrastar e soltar (atalho das mesmas ações dos botões) ---

  /** O que acontece ao soltar o cartão arrastado nesta coluna; `null` quando não há ação. */
  protected acaoDeSoltar(coluna: ColunaKanbanComercial): { texto: string; icon: string; tone: CesTone; acao: 'APROVAR' | 'REJEITAR' | 'ENTREGAR' } | null {
    const arraste = this.arrastando();
    if (!arraste || arraste.de === coluna) return null;
    const acoes = arraste.item.acoesPermitidas ?? [];
    if (arraste.de === 'APROVACAO' && coluna === 'ENTREGA' && acoes.includes('APROVAR')) return { texto: 'Solte para aprovar', icon: 'pi pi-check', tone: 'success', acao: 'APROVAR' };
    if (arraste.de === 'APROVACAO' && coluna === 'ENCERRADAS' && acoes.includes('REJEITAR')) return { texto: 'Solte para rejeitar', icon: 'pi pi-times', tone: 'danger', acao: 'REJEITAR' };
    if (arraste.de === 'ENTREGA' && coluna === 'ENCERRADAS' && acoes.includes('ENTREGAR')) return { texto: 'Solte para registrar a entrega', icon: 'pi pi-truck', tone: 'success', acao: 'ENTREGAR' };
    return null;
  }

  protected iniciarArraste(item: Card, de: ColunaKanbanComercial): void {
    this.arrastando.set({ item, de });
  }

  protected encerrarArraste(): void {
    this.arrastando.set(null);
    this.sobre.set(null);
  }

  protected aoPassar(evento: DragEvent, coluna: ColunaKanbanComercial): void {
    if (!this.acaoDeSoltar(coluna)) return;
    evento.preventDefault();
    if (evento.dataTransfer) evento.dataTransfer.dropEffect = 'move';
    this.sobre.set(coluna);
  }

  protected aoSair(evento: DragEvent, coluna: ColunaKanbanComercial): void {
    const destino = evento.relatedTarget as Node | null;
    if (destino && (evento.currentTarget as HTMLElement).contains(destino)) return;
    if (this.sobre() === coluna) this.sobre.set(null);
  }

  protected aoSoltar(evento: DragEvent, coluna: ColunaKanbanComercial): void {
    evento.preventDefault();
    const acao = this.acaoDeSoltar(coluna);
    const item = this.arrastando()?.item;
    this.encerrarArraste();
    if (!acao || !item) return;
    this.aoAgir(item, acao.acao, true);
  }

  protected aoAgir(item: Card, acao: AcaoCardComercial, porArraste = false): void {
    if (acao === 'APROVAR') void this.aprovar(item, porArraste);
    else if (acao === 'ENTREGAR') this.abrir.emit({ item, aba: 'entrega' });
    else if (acao === 'REJEITAR') this.rejeitar.emit(item);
    else this.abrir.emit({ item });
  }

  private async aprovar(item: Card, confirmar: boolean): Promise<void> {
    const id = item.id.slice(0, 8);
    if (confirmar) {
      const ok = await this.confirmacao.confirm({
        title: `Aprovar a solicitação #${id}?`,
        message: `${item.estoqueOrigemNome ?? 'Origem'} → ${item.estoqueDestinoNome ?? 'destino'}. Depois de aprovada, ela fica disponível para a retirada.`,
        confirmLabel: 'Aprovar'
      });
      if (!ok) return;
    }
    this.almoxarifado.aprovarSolicitacao(item.id, {}).subscribe({
      next: () => {
        this.notificacoes.success(`Solicitação #${id} aprovada. Ela agora aguarda a retirada.`);
        this.anuncio.set(`Solicitação ${id} aprovada e movida para Entrega.`);
        this.recarregar();
        this.alterado.emit();
      },
      error: (err) => {
        if (err?.status === 409) {
          this.notificacoes.warning('Outra pessoa alterou esta solicitação. O quadro foi atualizado.', { title: 'Conflito de atualização' });
          this.recarregar();
        } else {
          this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível aprovar' });
        }
      }
    });
  }
}

function porSituacao(faixas: FaixaStatus[]): GrupoKanban[] {
  return faixas.map((f) => ({
    id: f.status,
    status: f.status,
    label: SITUACAO[f.status]?.label ?? statusComercial(f.status).label,
    tone: SITUACAO[f.status]?.tone ?? 'neutral',
    itens: f.itens,
    total: f.total
  }));
}

function porPendencia(itens: Card[]): GrupoKanban[] {
  const grupos = new Map<string, Card[]>();
  const sem: Card[] = [];
  for (const it of itens) {
    const motivos = (it as { motivosPendencia?: string[] }).motivosPendencia ?? [];
    if (!it.possuiPendencia && !motivos.length) {
      sem.push(it);
      continue;
    }
    const chave = motivos.length === 1 ? motivos[0] : motivos.length > 1 ? 'Várias pendências' : 'Pendência sem motivo';
    grupos.set(chave, [...(grupos.get(chave) ?? []), it]);
  }
  const comPendencia = [...grupos.entries()]
    .sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0], 'pt-BR'))
    .map(([motivo, lista]): GrupoKanban => ({ id: `PEND:${motivo}`, label: motivo, tone: 'warning', itens: lista, total: lista.length }));
  return [...comPendencia, { id: 'PEND:-', label: 'Sem pendência', tone: 'success', itens: sem, total: sem.length }];
}

function porPrazo(itens: Card[]): GrupoKanban[] {
  const hoje = new Date();
  const hojeIso = isoCivil(hoje);
  const semana = isoCivil(new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() + 7));
  const dia = (s: Card) => s.dataPrevista?.substring(0, 10) ?? '';
  const faixas: { id: string; label: string; tone: CesTone; teste: (s: Card) => boolean }[] = [
    { id: 'PRAZO:ATRASADAS', label: 'Atrasadas', tone: 'danger', teste: (s) => saqueAtrasado(s, hoje) },
    { id: 'PRAZO:HOJE', label: 'Saque hoje', tone: 'warning', teste: (s) => dia(s) === hojeIso },
    { id: 'PRAZO:SEMANA', label: 'Próximos 7 dias', tone: 'info', teste: (s) => !!dia(s) && dia(s) > hojeIso && dia(s) <= semana },
    { id: 'PRAZO:DEPOIS', label: 'Mais adiante', tone: 'neutral', teste: (s) => !!dia(s) && dia(s) > semana },
    { id: 'PRAZO:SEM', label: 'Sem data de saque', tone: 'neutral', teste: () => true }
  ];
  const restantes = new Set(itens);
  return faixas
    .map((f): GrupoKanban => {
      const lista = [...restantes].filter(f.teste);
      lista.forEach((s) => restantes.delete(s));
      return { id: f.id, label: f.label, tone: f.tone, itens: lista, total: lista.length };
    })
    .filter((g) => g.total > 0);
}

function salvar(chave: string, valor: string): void {
  try {
    localStorage.setItem(chave, valor);
  } catch {
    /* preferência opcional */
  }
}

function lerAgrupamento(): AgrupamentoKanban {
  try {
    const v = localStorage.getItem(CHAVE_AGRUPAMENTO);
    return v === 'pendencia' || v === 'prazo' ? v : 'situacao';
  } catch {
    return 'situacao';
  }
}

function lerRecolhidos(): Record<string, boolean> {
  try {
    return JSON.parse(localStorage.getItem(CHAVE_RECOLHIDOS) ?? '{}') ?? {};
  } catch {
    return {};
  }
}
