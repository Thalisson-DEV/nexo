import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService
} from '@ces/ui';
import { catchError, map, of } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

import { CLASSES_TENSAO, ClasseTensao } from '../../../core/models/obras-kit.models';
import {
  CarteiraObraRequest,
  HistoricoStatusObraResponse,
  ItemOrcamentoObraResponse,
  ObraProximaResponse,
  ObraResponse,
  OrcamentoObraResponse,
  PRIORIDADES_OBRA,
  PrioridadeObra
} from '../../../core/models/obras.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ContratoService } from '../../../core/services/contrato.service';
import { ObrasService } from '../../../core/services/obras.service';
import { formatarDataHora, formatarDataParaApi, formatarMoeda, parseDataBrParaDate } from '../obras-ui';

const RAIOS: CesSelectOption<number>[] = [
  { label: '5 km', value: 5 },
  { label: '10 km', value: 10 },
  { label: '20 km', value: 20 },
  { label: '50 km', value: 50 }
];

type Carga<T> = { estado: 'loading' } | { estado: 'error'; mensagem: string } | { estado: 'ready'; dados: T };

/**
 * Seção de carteira da ficha da obra: dados de carteira (com edição), deslocamento da base,
 * histórico do status operacional, obras próximas e orçamento previsto importado.
 */
@Component({
  selector: 'app-obra-carteira',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  styles: `
    :host {
      display: block;
    }

    .carteira__bloco {
      margin-bottom: 1.5rem;
    }

    .carteira__titulo {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
      padding-left: 0.75rem;
      border-left: 2px solid var(--ces-brand);
    }

    .carteira__titulo h3 {
      margin: 0;
      color: var(--ces-foreground);
      font-size: 0.6875rem;
      font-weight: 500;
      letter-spacing: -0.01em;
      text-transform: uppercase;
    }

    .carteira__grade {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
      gap: 0.5rem 1.25rem;
      padding: 1rem;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-lg);
      background: var(--ces-surface-subtle);
    }

    .carteira__campo {
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
      min-width: 0;
    }

    .carteira__rotulo {
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      font-weight: 600;
    }

    .carteira__valor {
      color: var(--ces-foreground);
      font-size: 0.8125rem;
      font-weight: 600;
      overflow-wrap: anywhere;
    }

    .carteira__detalhe {
      color: var(--ces-foreground-subtle);
      font-size: var(--ces-font-caption);
    }

    .carteira__acoes {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }

    .carteira__filtro {
      max-width: 10rem;
    }

    @media print {
      .carteira__acoes,
      .carteira__filtro {
        display: none;
      }
    }
  `,
  template: `
    @if (obra(); as o) {
      <section class="carteira__bloco" aria-labelledby="carteira-titulo">
        <div class="carteira__titulo">
          <h3 id="carteira-titulo">Carteira</h3>
          @if (podeEscrever()) {
            <div class="carteira__acoes">
              <ces-button variant="ghost" size="sm" icon="pi pi-pencil" label="Editar carteira" (pressed)="abrirEdicao()"></ces-button>
            </div>
          }
        </div>
        <div class="carteira__grade">
          <div class="carteira__campo">
            <span class="carteira__rotulo">Contrato</span>
            <span class="carteira__valor">{{ nomeContrato(c()?.idContrato) }}</span>
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Classe de tensão</span>
            @if (c()?.classeTensaoRotulo) {
              <span class="carteira__valor">{{ c()!.classeTensaoRotulo }}</span>
              <span class="carteira__detalhe">Informada por {{ c()!.classeTensaoInformadaPor || '—' }} em {{ dataHora(c()!.classeTensaoInformadaEm) }}</span>
            } @else {
              <ces-status tone="warning" icon="pi-exclamation-triangle">Não informada</ces-status>
            }
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Prioridade</span>
            <span class="carteira__valor">{{ rotuloPrioridade(c()?.prioridade) }}</span>
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Prazo · prazo regulado</span>
            <span class="carteira__valor">{{ o.dataPrevisaoFim || '—' }} · {{ c()?.prazoRegulado || '—' }}</span>
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Lote previsto</span>
            <span class="carteira__valor">{{ c()?.lotePrevisto || '—' }}</span>
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Situação na carteira</span>
            <span class="carteira__valor">{{ c()?.situacaoCarteira || '—' }}</span>
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Material previsto</span>
            <span class="carteira__valor">{{ moeda(c()?.valorMaterialPrevisto) }}</span>
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Serviço previsto</span>
            <span class="carteira__valor">{{ moeda(c()?.valorServicoPrevisto) }}</span>
          </div>
          <div class="carteira__campo">
            <span class="carteira__rotulo">Deslocamento da base</span>
            @if (c()?.distanciaBaseKm != null) {
              <span class="carteira__valor">{{ km(c()!.distanciaBaseKm!) }} · {{ duracao(c()!.tempoDeslocamentoMin) }}</span>
              <span class="carteira__detalhe">
                @if (c()!.deslocamentoEstimado) {
                  <ces-status tone="warning" icon="pi-info-circle">Estimado</ces-status>
                }
                {{ o.baseOperacionalNome }} · calculado em {{ dataHora(c()!.deslocamentoCalculadoEm) }}
              </span>
            } @else if (o.latitude == null || o.longitude == null) {
              <span class="carteira__detalhe">Não calculado: a obra não tem coordenadas.</span>
            } @else {
              <span class="carteira__detalhe">Ainda não calculado.</span>
            }
            @if (podeEscrever() && o.latitude != null && o.longitude != null) {
              <span class="carteira__acoes">
                <ces-button variant="ghost" size="sm" icon="pi pi-refresh" label="Recalcular" [loading]="recalculando()" (pressed)="recalcular()"></ces-button>
              </span>
            }
          </div>
          @if (c()?.observacaoStatus) {
            <div class="carteira__campo">
              <span class="carteira__rotulo">Observação do status</span>
              <span class="carteira__valor">{{ c()!.observacaoStatus }}</span>
            </div>
          }
        </div>
      </section>

      <section class="carteira__bloco" aria-labelledby="historico-status-titulo">
        <div class="carteira__titulo"><h3 id="historico-status-titulo">Histórico do status</h3></div>
        @switch (historico().estado) {
          @case ('loading') { <ces-async-state state="loading" [compact]="true" title="Carregando o histórico"></ces-async-state> }
          @case ('error') {
            <ces-async-state state="error" [compact]="true" title="Não foi possível carregar o histórico" [description]="mensagem(historico())"
              actionLabel="Tentar de novo" (action)="carregarHistorico()"></ces-async-state>
          }
          @default {
            <ces-data-table caption="Mudanças de status" [searchable]="false" [columns]="colunasHistorico" [rows]="dados(historico()) ?? []"
              [rowKey]="chave" [pageSizeOptions]="[5, 10, 25]" [pageSize]="5"
              [state]="dados(historico())?.length ? 'ready' : 'empty'" stateTitle="Sem mudanças de status registradas"></ces-data-table>
          }
        }
      </section>

      <section class="carteira__bloco" aria-labelledby="proximas-titulo">
        <div class="carteira__titulo">
          <h3 id="proximas-titulo">Obras próximas</h3>
          @if (o.latitude != null && o.longitude != null) {
            <div class="carteira__filtro">
              <ces-select label="Raio" [labelVisible]="false" [options]="raios" [value]="raio()" (valueChange)="raio.set($event ?? 10)"></ces-select>
            </div>
          }
        </div>
        @if (o.latitude == null || o.longitude == null) {
          <ces-async-state state="empty" [compact]="true" icon="pi pi-map-marker" title="Sem coordenadas"
            description="Informe latitude e longitude da obra para ver as obras ativas na região."></ces-async-state>
        } @else {
          @switch (proximas().estado) {
            @case ('loading') { <ces-async-state state="loading" [compact]="true" title="Buscando obras próximas"></ces-async-state> }
            @case ('error') {
              <ces-async-state state="error" [compact]="true" title="Não foi possível buscar as obras próximas" [description]="mensagem(proximas())"
                actionLabel="Tentar de novo" (action)="carregarProximas()"></ces-async-state>
            }
            @default {
              <ces-data-table [caption]="'Obras ativas em até ' + raio() + ' km (linha reta)'" [searchable]="false" [columns]="colunasProximas"
                [rows]="dados(proximas()) ?? []" [rowKey]="chave" [pageSizeOptions]="[10, 25, 50]"
                [state]="dados(proximas())?.length ? 'ready' : 'empty'" [stateTitle]="'Nenhuma obra ativa em até ' + raio() + ' km'">
                <ng-template cesCell="numero" let-row>
                  <a class="ces-cell-strong" [routerLink]="['/obras', row.id]">{{ row.numero }}</a>
                  <span class="ces-cell-muted">{{ row.titulo }}</span>
                </ng-template>
              </ces-data-table>
            }
          }
        }
      </section>

      <section class="carteira__bloco" aria-labelledby="orcamento-titulo">
        <div class="carteira__titulo"><h3 id="orcamento-titulo">Orçamento previsto</h3></div>
        @switch (orcamento().estado) {
          @case ('loading') { <ces-async-state state="loading" [compact]="true" title="Carregando o orçamento"></ces-async-state> }
          @case ('error') {
            <ces-async-state state="error" [compact]="true" title="Não foi possível carregar o orçamento" [description]="mensagem(orcamento())"
              actionLabel="Tentar de novo" (action)="carregarOrcamento()"></ces-async-state>
          }
          @default {
            @if (dados(orcamento()); as orc) {
              @if (!orc.importado) {
                <ces-async-state state="empty" [compact]="true" icon="pi pi-calculator" title="Não há orçamento importado"
                  description="Importe os serviços previstos pela listagem de obras (Importar · Serviços e orçamento previstos)."></ces-async-state>
              } @else {
                <div class="ces-kpis">
                  <div class="ces-kpi"><span class="ces-kpi__body"><span class="ces-kpi__label">Serviços importados</span><span class="ces-kpi__value">{{ moeda(orc.totalServicosImportados) }}</span>
                    <span class="ces-kpi__detail">{{ orc.itens.length }} {{ orc.itens.length === 1 ? 'serviço' : 'serviços' }} · importado em {{ dataHora(orc.dataImportacao) }}</span></span></div>
                  <div class="ces-kpi"><span class="ces-kpi__body"><span class="ces-kpi__label">Serviço previsto</span><span class="ces-kpi__value">{{ moeda(orc.valorServicoPrevisto) }}</span></span></div>
                  <div class="ces-kpi"><span class="ces-kpi__body"><span class="ces-kpi__label">Material previsto</span><span class="ces-kpi__value">{{ moeda(orc.valorMaterialPrevisto) }}</span></span></div>
                </div>
                <ces-data-table caption="Serviços previstos" [columns]="colunasOrcamento" [rows]="orc.itens" [rowKey]="chave"
                  searchPlaceholder="Código ou descrição" [pageSizeOptions]="[10, 25, 50]">
                  <ng-template cesCell="codigo" let-row>
                    <span class="ces-cell-strong">{{ row.codigo }}</span>
                    @if (!row.idAcao) {
                      <ces-status tone="warning" icon="pi-exclamation-triangle">Sem ação no contrato</ces-status>
                    }
                  </ng-template>
                </ces-data-table>
              }
            }
          }
        }
      </section>

      <ces-form-drawer [(visible)]="edicaoAberta" title="Editar carteira" [description]="o.numero + ' · ' + o.titulo" [dirty]="alterado()" size="md">
        <ces-form-section title="Enquadramento" icon="pi pi-briefcase">
          <div class="ces-form-grid">
            <ces-select label="Contrato" [options]="contratoOpcoes()" [(value)]="idContrato" [filter]="true" [clearable]="true"
              description="Define os kits disponíveis no planejamento."></ces-select>
            <ces-select label="Classe de tensão" [options]="tensoes" [(value)]="classeTensao" [clearable]="true"
              description="Guarda quem informou e quando; filtra os kits aplicáveis."></ces-select>
            <ces-select label="Prioridade" [options]="prioridades" [(value)]="prioridade" [clearable]="true"></ces-select>
            <ces-datepicker label="Prazo regulado" [(value)]="prazoRegulado" [clearable]="true"></ces-datepicker>
            <ces-text-input label="Lote previsto" [(value)]="lotePrevisto" [maxlength]="40"></ces-text-input>
            <ces-text-input label="Situação na carteira" [(value)]="situacaoCarteira" [maxlength]="60" placeholder="Ex.: Liberada, Aguardando licença"></ces-text-input>
          </div>
        </ces-form-section>
        <ces-form-section title="Valores previstos" icon="pi pi-wallet">
          <div class="ces-form-grid">
            <ces-number-input label="Valor de projeto" mode="currency" [min]="0" [(value)]="valorProjeto"></ces-number-input>
            <ces-number-input label="Material previsto" mode="currency" [min]="0" [(value)]="valorMaterial"></ces-number-input>
            <ces-number-input label="Serviço previsto" mode="currency" [min]="0" [(value)]="valorServico"></ces-number-input>
          </div>
        </ces-form-section>
        <div drawer-footer class="ces-actions-row">
          <ces-button variant="ghost" label="Cancelar" (pressed)="edicaoAberta.set(false)"></ces-button>
          <ces-button icon="pi pi-check" label="Salvar carteira" [loading]="salvando()" (pressed)="salvar()"></ces-button>
        </div>
      </ces-form-drawer>
    }
  `
})
export class ObraCarteiraComponent {
  private readonly service = inject(ObrasService);
  private readonly contratos = inject(ContratoService);
  private readonly notificacoes = inject(NotificationService);

  readonly obra = input.required<ObraResponse>();
  readonly podeEscrever = input(false);
  /** Emite depois de salvar a carteira ou recalcular o deslocamento, para a ficha recarregar a obra. */
  readonly alterada = output<void>();

  protected readonly c = computed(() => this.obra().carteira ?? null);
  protected readonly moeda = formatarMoeda;
  protected readonly dataHora = formatarDataHora;
  protected readonly raios = RAIOS;
  protected readonly tensoes = CLASSES_TENSAO;
  protected readonly prioridades = PRIORIDADES_OBRA;

  protected readonly historico = signal<Carga<HistoricoStatusObraResponse[]>>({ estado: 'loading' });
  protected readonly proximas = signal<Carga<ObraProximaResponse[]>>({ estado: 'loading' });
  protected readonly orcamento = signal<Carga<OrcamentoObraResponse>>({ estado: 'loading' });
  protected readonly raio = signal(10);
  protected readonly recalculando = signal(false);

  protected readonly contratoOpcoes = toSignal(
    this.contratos.listarContratosAtivos().pipe(
      map((p) => p.content.map((ct) => ({ label: `${ct.numeroContrato} · ${ct.cliente}`, value: ct.id }))),
      catchError(() => of([] as CesSelectOption<string>[]))
    ),
    { initialValue: [] as CesSelectOption<string>[] }
  );

  protected readonly colunasHistorico: CesDataTableColumn<HistoricoStatusObraResponse>[] = [
    { key: 'data', header: 'Quando', width: '10rem', priority: 1, value: (h) => h.data, display: (h) => formatarDataHora(h.data) },
    { key: 'mudanca', header: 'Status', priority: 1, value: (h) => h.statusNovoNome,
      display: (h) => (h.statusAnteriorNome && h.statusAnteriorNome !== h.statusNovoNome ? `${h.statusAnteriorNome} → ${h.statusNovoNome}` : h.statusNovoNome) },
    { key: 'observacao', header: 'Observação', display: (h) => h.observacao ?? '—' },
    { key: 'usuario', header: 'Usuário', width: '10rem', display: (h) => h.usuario ?? '—' }
  ];
  protected readonly colunasProximas: CesDataTableColumn<ObraProximaResponse>[] = [
    { key: 'numero', header: 'Obra', type: 'custom', priority: 1, value: (p) => p.numero, display: (p) => `${p.numero} ${p.titulo}` },
    { key: 'statusNome', header: 'Status', display: (p) => p.statusNome ?? '—' },
    { key: 'municipioNome', header: 'Município', display: (p) => p.municipioNome ?? '—' },
    { key: 'distanciaKm', header: 'Distância (km)', type: 'number', priority: 1, width: '8rem' }
  ];
  protected readonly colunasOrcamento: CesDataTableColumn<ItemOrcamentoObraResponse>[] = [
    { key: 'codigo', header: 'Código', type: 'custom', priority: 1, width: '10rem', value: (i) => i.codigo, display: (i) => i.codigo },
    { key: 'descricao', header: 'Descrição', priority: 1, display: (i) => i.descricao ?? '' },
    { key: 'unidade', header: 'Un.', width: '4rem', display: (i) => i.unidade ?? '' },
    { key: 'quantidade', header: 'Qtd.', type: 'number', width: '6rem' },
    { key: 'valorUnitario', header: 'Valor unit.', type: 'currency', width: '8rem' },
    { key: 'valorTotal', header: 'Total', type: 'currency', priority: 1, width: '9rem' }
  ];
  protected readonly chave = (r: { id: string }) => r.id;

  // Edição da carteira
  protected readonly edicaoAberta = signal(false);
  protected readonly salvando = signal(false);
  protected readonly idContrato = signal<string | null>(null);
  protected readonly classeTensao = signal<ClasseTensao | null>(null);
  protected readonly prioridade = signal<PrioridadeObra | null>(null);
  protected readonly prazoRegulado = signal<Date | null>(null);
  protected readonly lotePrevisto = signal('');
  protected readonly situacaoCarteira = signal('');
  protected readonly valorProjeto = signal<number | null>(null);
  protected readonly valorMaterial = signal<number | null>(null);
  protected readonly valorServico = signal<number | null>(null);
  private originais = '';

  protected readonly alterado = computed(() => this.assinatura() !== this.originais);

  constructor() {
    // Recarrega os blocos quando a ficha troca de obra; o raio recarrega só as próximas.
    effect(() => {
      const id = this.obra().id;
      untracked(() => {
        this.carregarHistorico(id);
        this.carregarOrcamento(id);
      });
    });
    effect(() => {
      const o = this.obra();
      const raio = this.raio();
      if (o.latitude != null && o.longitude != null) untracked(() => this.carregarProximas(o.id, raio));
    });
  }

  protected dados<T>(carga: Carga<T>): T | null {
    return carga.estado === 'ready' ? carga.dados : null;
  }

  protected mensagem(carga: Carga<unknown>): string | undefined {
    return carga.estado === 'error' ? carga.mensagem : undefined;
  }

  protected nomeContrato(id?: string | null): string {
    if (!id) return 'Não informado';
    return this.contratoOpcoes().find((o) => o.value === id)?.label ?? 'Contrato inativo';
  }

  protected rotuloPrioridade(p?: PrioridadeObra | null): string {
    return PRIORIDADES_OBRA.find((o) => o.value === p)?.label ?? '—';
  }

  protected km(valor: number): string {
    return `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km`;
  }

  protected duracao(minutos?: number | null): string {
    if (minutos == null) return '—';
    const h = Math.floor(minutos / 60);
    return h ? `${h} h ${String(minutos % 60).padStart(2, '0')} min` : `${minutos} min`;
  }

  carregarHistorico(id = this.obra().id): void {
    this.historico.set({ estado: 'loading' });
    this.service.listarHistoricoStatus(id).subscribe({
      next: (dados) => this.historico.set({ estado: 'ready', dados }),
      error: (e) => this.historico.set({ estado: 'error', mensagem: getErrorMessage(e) })
    });
  }

  protected carregarProximas(id = this.obra().id, raio = this.raio()): void {
    this.proximas.set({ estado: 'loading' });
    this.service.listarObrasProximas(id, raio).subscribe({
      next: (dados) => this.proximas.set({ estado: 'ready', dados }),
      error: (e) => this.proximas.set({ estado: 'error', mensagem: getErrorMessage(e) })
    });
  }

  protected carregarOrcamento(id = this.obra().id): void {
    this.orcamento.set({ estado: 'loading' });
    this.service.buscarOrcamento(id).subscribe({
      next: (dados) => this.orcamento.set({ estado: 'ready', dados }),
      error: (e) => this.orcamento.set({ estado: 'error', mensagem: getErrorMessage(e) })
    });
  }

  protected recalcular(): void {
    this.recalculando.set(true);
    this.service.recalcularDeslocamento(this.obra().id).subscribe({
      next: (carteira) => {
        this.recalculando.set(false);
        this.notificacoes.success(carteira.deslocamentoEstimado ? 'Deslocamento estimado (provedor de rotas indisponível).' : 'Deslocamento recalculado.');
        this.alterada.emit();
      },
      error: (e) => {
        this.recalculando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected abrirEdicao(): void {
    const o = this.obra();
    const c = o.carteira;
    this.idContrato.set(c?.idContrato ?? null);
    this.classeTensao.set(c?.classeTensao ?? null);
    this.prioridade.set(c?.prioridade ?? null);
    this.prazoRegulado.set(parseDataBrParaDate(c?.prazoRegulado));
    this.lotePrevisto.set(c?.lotePrevisto ?? '');
    this.situacaoCarteira.set(c?.situacaoCarteira ?? '');
    this.valorProjeto.set(o.valorProjeto ?? null);
    this.valorMaterial.set(c?.valorMaterialPrevisto ?? null);
    this.valorServico.set(c?.valorServicoPrevisto ?? null);
    this.originais = this.assinatura();
    this.edicaoAberta.set(true);
  }

  private assinatura(): string {
    return JSON.stringify([
      this.idContrato(), this.classeTensao(), this.prioridade(), formatarDataParaApi(this.prazoRegulado()) ?? null,
      this.lotePrevisto(), this.situacaoCarteira(), this.valorProjeto(), this.valorMaterial(), this.valorServico()
    ]);
  }

  protected salvar(): void {
    const request: CarteiraObraRequest = {
      idContrato: this.idContrato(),
      classeTensao: this.classeTensao(),
      prioridade: this.prioridade(),
      prazoRegulado: formatarDataParaApi(this.prazoRegulado()) ?? null,
      lotePrevisto: this.lotePrevisto().trim() || null,
      situacaoCarteira: this.situacaoCarteira().trim() || null,
      valorProjeto: this.valorProjeto(),
      valorMaterialPrevisto: this.valorMaterial(),
      valorServicoPrevisto: this.valorServico()
    };
    this.salvando.set(true);
    this.service.informarCarteira(this.obra().id, request).subscribe({
      next: () => {
        this.salvando.set(false);
        this.edicaoAberta.set(false);
        this.notificacoes.success('Carteira da obra atualizada.');
        this.alterada.emit();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }
}
