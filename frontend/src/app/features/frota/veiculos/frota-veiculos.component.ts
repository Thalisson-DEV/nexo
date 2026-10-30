import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesListShortcutsDirective,
  CesMultiSelectComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesStatusComponent,
  CesTone,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import {
  OpcoesFrota,
  ROTULO_REGIME,
  ROTULO_SITUACAO,
  RegimeVeiculo,
  SituacaoOperacional,
  TOM_SITUACAO,
  TipoVeiculo,
  VeiculoFrotaDetalhe,
  VeiculoFrotaResumo,
  contar,
  formatarMedida,
  formatarPlaca
} from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService } from '../../../core/services/frota.service';
import { acessoFrota } from '../shared/frota-acesso';
import { LIMITE_EXPORTACAO_FROTA, OPCOES_CADASTRO, basesFrota, exportacaoFrota, paginaFrota } from '../shared/frota-lista';
import { FrotaVeiculoFormComponent } from './frota-veiculo-form.component';

@Component({
  selector: 'app-frota-veiculos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    FrotaVeiculoFormComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="acesso.editarVeiculo()" (newRequested)="formularioAberto.set(true)">
      <ces-page-header
        title="Veículos"
        icon="pi pi-car"
        [breadcrumb]="[{ label: 'Frota' }, { label: 'Veículos' }]"
        description="Situação, condutor, quilometragem e pendências de cada placa. Clique numa linha para abrir a ficha. Atalhos: / busca, F filtros, N novo veículo."
      >
        @if (acesso.editarVeiculo()) {
          <ces-button actions icon="pi pi-plus" label="Novo veículo" [keyShortcut]="atalhos.newShortcut()" (pressed)="formularioAberto.set(true)"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel
        #painel
        storageKey="frota-veiculos"
        [activeCount]="lista.activeCount()"
        [summary]="lista.summary()"
        [loading]="resultado.loading()"
        (applied)="lista.apply()"
        (cleared)="lista.reset()"
        (chipRemoved)="lista.remove($any($event))"
      >
        <div class="ces-filter-grid">
          <ces-multiselect label="Base" [options]="bases.opcoes()" [(value)]="lista.filters.idsBase" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Tipo" [options]="tiposOpcoes()" [(value)]="lista.filters.idsTipoVeiculo" placeholder="Todos"></ces-multiselect>
          <ces-multiselect label="Situação" [options]="situacoesOpcoes()" [(value)]="lista.filters.situacoes" [filter]="false" placeholder="Todas"></ces-multiselect>
          <ces-select label="Regime" [options]="regimesOpcoes()" [(value)]="lista.filters.regime" [clearable]="true" placeholder="Todos"></ces-select>
          <ces-select label="Cadastro" [options]="cadastroOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Veículos"
        mode="server"
        [description]="descricaoTabela()"
        storageKey="frota-veiculos"
        exportFileName="frota-veiculos"
        rowActionLabel="Abrir ficha"
        searchPlaceholder="Placa, chassi ou renavam"
        [emptyActionLabel]="acesso.editarVeiculo() ? 'Novo veículo' : undefined"
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowKey]="chave"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os veículos' : 'Nenhum veículo cadastrado'"
        [stateDescription]="resultado.error() ?? 'Para incluir uma placa, use Novo veículo.'"
        noResultsTitle="Nenhum veículo encontrado com esse recorte."
        [filtered]="lista.filtered()"
        [exportRows]="exportar"
        [exportLimit]="limiteExportacao"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="formularioAberto.set(true)"
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="placa" let-row>
          <span class="ces-cell-strong">{{ placa(row.placa) }}</span>
          <span class="ces-cell-muted">{{ row.fabricante }} {{ row.modelo }}{{ row.anoModelo ? ' · ' + row.anoModelo : '' }}</span>
        </ng-template>
        <ng-template cesCell="situacao" let-row>
          <ces-status [tone]="tomSituacao[row.situacao]">{{ rotuloSituacao[row.situacao] }}</ces-status>
          @if (!row.ativo) {
            <ces-status tone="neutral" emphasis="outline">Desativado</ces-status>
          }
        </ng-template>
        <ng-template cesCell="kmAtual" let-row>
          @if (row.kmAtual != null) {
            <span class="ces-cell-num">{{ medida(row.kmAtual, 'km') }}</span>
          } @else {
            <span class="ces-cell-muted">Sem leitura</span>
          }
        </ng-template>
        <ng-template cesCell="condutorAtual" let-row>
          @if (row.condutorAtual) {
            {{ row.condutorAtual }}
          } @else {
            <span class="ces-cell-muted">Sem condutor</span>
          }
        </ng-template>
        <ng-template cesCell="pendencias" let-row>
          <span class="ces-cell-chips">
            @if (row.documentosVencidos) {
              <ces-status tone="danger" icon="pi-file">{{ contar(row.documentosVencidos, 'doc. vencido', 'docs. vencidos') }}</ces-status>
            }
            @if (row.documentosAVencer) {
              <ces-status tone="warning" icon="pi-clock">{{ contar(row.documentosAVencer, 'doc. a vencer', 'docs. a vencer') }}</ces-status>
            }
            @if (row.leiturasInconsistentes) {
              <ces-status tone="warning" icon="pi-exclamation-triangle">{{ row.leiturasInconsistentes }} KM a conferir</ces-status>
            }
            @if (!row.documentosVencidos && !row.documentosAVencer && !row.leiturasInconsistentes) {
              <ces-status tone="success" icon="pi-check">Em dia</ces-status>
            }
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <app-frota-veiculo-form
      [(visivel)]="formularioAberto"
      [opcoes]="opcoes()"
      [tiposVeiculo]="tiposVeiculo()"
      (salvo)="aoCadastrar($event)"
    ></app-frota-veiculo-form>
  `
})
export class FrotaVeiculosComponent {
  private readonly frota = inject(FrotaService);
  private readonly router = inject(Router);
  private readonly notificacoes = inject(NotificationService);
  protected readonly acesso = acessoFrota();
  protected readonly bases = basesFrota();

  protected readonly tomSituacao: Record<string, CesTone> = TOM_SITUACAO;
  protected readonly rotuloSituacao: Record<string, string> = ROTULO_SITUACAO;
  protected readonly placa = formatarPlaca;
  protected readonly medida = formatarMedida;
  protected readonly contar = contar;
  protected readonly cadastroOpcoes = OPCOES_CADASTRO;

  protected readonly opcoes = signal<OpcoesFrota | null>(null);
  protected readonly tiposVeiculo = signal<TipoVeiculo[]>([]);
  protected readonly formularioAberto = signal(false);

  protected readonly tiposOpcoes = computed(() => this.tiposVeiculo().map((t) => ({ label: t.nome, value: t.id })));
  protected readonly situacoesOpcoes = computed(() =>
    (this.opcoes()?.situacoes ?? []).map((o) => ({ label: o.rotulo, value: o.valor }))
  );
  protected readonly regimesOpcoes = computed(() =>
    (this.opcoes()?.regimes ?? []).map((o) => ({ label: o.rotulo, value: o.valor }))
  );

  protected readonly lista = cesListState({
    storageKey: 'frota-veiculos',
    filters: {
      idsBase: cesFilter.list<string>([], { label: 'Base', param: 'base', itemLabel: (id) => this.bases.nome(id), countLabel: (n) => `${n} selecionadas` }),
      idsTipoVeiculo: cesFilter.list<string>([], {
        label: 'Tipo',
        param: 'tipo',
        itemLabel: (id) => this.tiposVeiculo().find((t) => t.id === id)?.nome ?? 'tipo selecionado'
      }),
      situacoes: cesFilter.list<SituacaoOperacional>([], {
        label: 'Situação',
        param: 'situacao',
        itemLabel: (s) => ROTULO_SITUACAO[s],
        countLabel: (n) => `${n} selecionadas`
      }),
      regime: cesFilter.value<RegimeVeiculo | null>(null, { label: 'Regime', format: (r) => (r ? ROTULO_REGIME[r] : null) }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Cadastro', options: () => OPCOES_CADASTRO })
    },
    sort: [{ key: 'placa', direction: 'asc' }]
  });

  protected readonly resultado = this.lista.results<VeiculoFrotaResumo>(
    (pedido) => this.frota.listarVeiculos(this.filtro(), paginaFrota(pedido)),
    { errorMessage: getErrorMessage }
  );

  protected readonly descricaoTabela = computed(() =>
    this.resultado.loading() && !this.resultado.total() ? undefined : contar(this.resultado.total(), 'veículo', 'veículos', 'Nenhum veículo')
  );

  protected readonly colunas: CesDataTableColumn<VeiculoFrotaResumo>[] = [
    { key: 'placa', header: 'Placa', type: 'custom', priority: 1, sortField: 'placa', value: (r) => r.placa, display: (r) => `${r.placa} ${r.modelo ?? ''}` },
    { key: 'baseOperacional', header: 'Base', priority: 1 },
    { key: 'tipoVeiculo', header: 'Tipo', sortField: 'tipoVeiculo.nome', display: (r) => r.tipoVeiculo ?? 'Sem tipo' },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, sortField: 'situacao', value: (r) => r.situacao, display: (r) => ROTULO_SITUACAO[r.situacao] },
    { key: 'regime', header: 'Regime', sortField: 'regime', display: (r) => ROTULO_REGIME[r.regime] },
    { key: 'kmAtual', header: 'KM atual', type: 'custom', value: (r) => r.kmAtual, display: (r) => formatarMedida(r.kmAtual, 'km', 0, '') },
    { key: 'condutorAtual', header: 'Condutor', type: 'custom', value: (r) => r.condutorAtual, display: (r) => r.condutorAtual ?? 'Sem condutor' },
    { key: 'implementos', header: 'Implementos', value: (r) => r.implementosAcoplados.join(', '), display: (r) => r.implementosAcoplados.join(', ') || '—' },
    {
      key: 'pendencias',
      header: 'Pendências',
      type: 'custom',
      value: (r) => r.documentosVencidos * 100 + r.documentosAVencer * 10 + r.leiturasInconsistentes,
      display: (r) => `${r.documentosVencidos} vencidos, ${r.documentosAVencer} a vencer, ${r.leiturasInconsistentes} KM a conferir`
    }
  ];

  protected readonly chave = (r: VeiculoFrotaResumo) => r.idVeiculo;
  protected readonly rotulo = (r: VeiculoFrotaResumo) => `Veículo ${r.placa}`;
  protected readonly limiteExportacao = LIMITE_EXPORTACAO_FROTA;
  protected readonly exportar = exportacaoFrota(
    (pagina) => this.frota.listarVeiculos(this.filtro(), pagina),
    () => this.lista.request()
  );

  constructor() {
    this.frota.opcoes().subscribe({ next: (o) => this.opcoes.set(o), error: () => undefined });
    this.frota.tiposVeiculo().subscribe({ next: (p) => this.tiposVeiculo.set(p.content), error: () => undefined });
  }

  protected abrir(veiculo: VeiculoFrotaResumo): void {
    void this.router.navigate(['/frota/veiculos', veiculo.idVeiculo]);
  }

  protected aoCadastrar(veiculo: VeiculoFrotaDetalhe): void {
    this.notificacoes.info('Abrindo a ficha do veículo cadastrado.');
    void this.router.navigate(['/frota/veiculos', veiculo.idVeiculo]);
  }

  private filtro() {
    const pedido = this.lista.request();
    return { ...pedido.filters, busca: pedido.query || null };
  }
}
