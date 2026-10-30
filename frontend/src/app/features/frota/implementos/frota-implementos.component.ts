import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesRowActionsDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesMultiSelectComponent,
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  CesTone,
  cesFilter,
  cesListState
} from '@ces/ui';
import { forkJoin } from 'rxjs';
import {
  AcoplamentoImplemento,
  Implemento,
  ImplementoRequest,
  OpcoesFrota,
  ROTULO_SITUACAO,
  SituacaoOperacional,
  TOM_SITUACAO,
  TipoImplemento,
  VeiculoFrotaResumo,
  contar,
  formatarPlaca,
  paraInstanteIso
} from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService, PAGINA_OPCOES } from '../../../core/services/frota.service';
import { acessoFrota } from '../shared/frota-acesso';
import { LIMITE_EXPORTACAO_FROTA, basesFrota, exportacaoFrota, paginaFrota } from '../shared/frota-lista';

type Modo = 'cadastro' | 'acoplar' | 'desacoplar' | 'historico';

@Component({
  selector: 'app-frota-implementos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesRowActionsDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesMultiSelectComponent,
    CesNumberInputComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="acesso.editarVeiculo()" (newRequested)="abrirCadastro(null)">
      <ces-page-header
        title="Implementos"
        icon="pi pi-link"
        [breadcrumb]="[{ label: 'Frota' }, { label: 'Implementos' }]"
        description="Cestos aéreos, guindautos, reboques e demais equipamentos, com o veículo em que estão acoplados e o histórico de onde já estiveram. Atalhos: / busca, F filtros, N novo implemento."
      >
        @if (acesso.editarVeiculo()) {
          <ces-button actions icon="pi pi-plus" label="Novo implemento" [keyShortcut]="atalhos.newShortcut()" (pressed)="abrirCadastro(null)"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel
        #painel
        storageKey="frota-implementos"
        [activeCount]="lista.activeCount()"
        [summary]="lista.summary()"
        [loading]="resultado.loading()"
        (applied)="lista.apply()"
        (cleared)="lista.reset()"
        (chipRemoved)="lista.remove($any($event))"
      >
        <div class="ces-filter-grid">
          <ces-multiselect label="Base" [options]="bases.opcoes()" [(value)]="lista.filters.idsBase" placeholder="Todas"></ces-multiselect>
          <ces-multiselect label="Tipo" [options]="tiposOpcoes()" [(value)]="lista.filters.idsTipoImplemento" placeholder="Todos"></ces-multiselect>
          <ces-select label="Acoplamento" [options]="acopladoOpcoes" [(value)]="lista.filters.acoplado" [clearable]="true" placeholder="Todos"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Implementos"
        mode="server"
        [description]="descricaoTabela()"
        searchPlaceholder="Identificação, fabricante ou nº de série"
        storageKey="frota-implementos"
        exportFileName="frota-implementos"
        [rowActionLabel]="acesso.editarVeiculo() ? 'Editar' : 'Ver histórico'"
        [rowActionIcon]="acesso.editarVeiculo() ? 'pi pi-pencil' : 'pi pi-history'"
        [emptyActionLabel]="acesso.editarVeiculo() ? 'Novo implemento' : undefined"
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowKey]="chave"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os implementos' : 'Nenhum implemento cadastrado'"
        [stateDescription]="resultado.error() ?? 'Os tipos de implemento são cadastrados em Configurações.'"
        noResultsTitle="Nenhum implemento encontrado com esse recorte."
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
        (emptyAction)="abrirCadastro(null)"
        (rowActivate)="acesso.editarVeiculo() ? abrirCadastro($event) : abrirHistorico($event)"
      >
        <ng-template cesCell="identificacao" let-row>
          <span class="ces-cell-strong">{{ row.identificacao }}</span>
          <span class="ces-cell-muted">{{ row.tipoImplemento }}{{ row.fabricante ? ' · ' + row.fabricante : '' }}{{ row.modelo ? ' ' + row.modelo : '' }}</span>
        </ng-template>
        <ng-template cesCell="situacao" let-row>
          <ces-status [tone]="tomSituacao[row.situacao]">{{ rotuloSituacao[row.situacao] }}</ces-status>
          @if (!row.ativo) {
            <ces-status tone="neutral" emphasis="outline">Desativado</ces-status>
          }
        </ng-template>
        <ng-template cesCell="veiculo" let-row>
          @if (row.acoplamentoAtual) {
            <span class="ces-cell-strong">{{ placa(row.acoplamentoAtual.placa) }}</span>
            <span class="ces-cell-muted">desde {{ row.acoplamentoAtual.inicio | date: 'dd/MM/yyyy' }}</span>
          } @else {
            <ces-status tone="neutral" emphasis="outline">Livre</ces-status>
          }
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            @if (acesso.editarVeiculo()) {
              <ces-icon-button icon="pi pi-history" [label]="'Histórico de acoplamentos de ' + row.identificacao" tooltip="Histórico de acoplamentos"
                size="icon-sm" (pressed)="abrirHistorico(row)"></ces-icon-button>
            }
            @if (acesso.editarVeiculo() && row.ativo) {
              @if (row.acoplamentoAtual) {
                <ces-icon-button icon="pi pi-arrow-right-arrow-left" [label]="'Mudar ' + row.identificacao + ' de veículo'" tooltip="Mudar de veículo"
                  size="icon-sm" (pressed)="abrir('acoplar', row)"></ces-icon-button>
                <ces-icon-button icon="pi pi-times-circle" [label]="'Desacoplar ' + row.identificacao" tooltip="Desacoplar" size="icon-sm"
                  (pressed)="abrir('desacoplar', row)"></ces-icon-button>
              } @else {
                <ces-icon-button icon="pi pi-link" [label]="'Acoplar ' + row.identificacao + ' a um veículo'" tooltip="Acoplar a um veículo"
                  size="icon-sm" (pressed)="abrir('acoplar', row)"></ces-icon-button>
              }
            }
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [visible]="modo() === 'cadastro'" (visibleChange)="!$event && fechar()"
      [title]="selecionado() ? 'Editar implemento' : 'Novo implemento'"
      [description]="selecionado() ? selecionado()!.identificacao + ' · ' + selecionado()!.tipoImplemento : 'Equipamento que pode ser acoplado a um veículo da frota.'"
      [dirty]="!!identificacao().trim()" [loading]="salvando()" size="md">
      <ces-form-section title="Implemento" icon="pi pi-cog">
        <div class="ces-form-grid">
          <ces-text-input label="Identificação" [required]="true" [(value)]="identificacao" [maxlength]="60"
            description="Código que a operação usa para o equipamento." [error]="erroCampo('identificacao')"></ces-text-input>
          <ces-select label="Tipo" [required]="true" [options]="tiposOpcoesAtivos()" [(value)]="idTipo" [filter]="true"
            [error]="erroCampo('tipo')"></ces-select>
          <ces-select label="Base operacional" [required]="true" [options]="bases.opcoes()" [(value)]="idBase" [filter]="true"
            [error]="erroCampo('base')"></ces-select>
          <ces-text-input label="Fabricante" [(value)]="fabricante" [maxlength]="80" [showOptional]="true"></ces-text-input>
          <ces-text-input label="Modelo" [(value)]="modelo" [maxlength]="100" [showOptional]="true"></ces-text-input>
          <ces-text-input label="Número de série" [(value)]="numeroSerie" [maxlength]="80" [showOptional]="true"></ces-text-input>
          <ces-number-input label="Ano de fabricação" [(value)]="ano" [useGrouping]="false" [min]="1950" [showOptional]="true"></ces-number-input>
          @if (selecionado()) {
            <ces-select label="Situação" [options]="situacoesOpcoes()" [(value)]="situacao"></ces-select>
          }
        </div>
        <ces-textarea label="Observação" [(value)]="observacao" [rows]="2" [maxlength]="1000" [showOptional]="true"></ces-textarea>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        @if (selecionado(); as atual) {
          <ces-button [variant]="atual.ativo ? 'destructive' : 'outline'" [icon]="atual.ativo ? 'pi pi-ban' : 'pi pi-replay'"
            [label]="atual.ativo ? 'Desativar implemento' : 'Reativar implemento'" [disabled]="salvando()" (pressed)="alternar(atual)"></ces-button>
        }
        <ces-button variant="ghost" label="Cancelar" (pressed)="fechar()"></ces-button>
        <ces-button icon="pi pi-check" [label]="selecionado() ? 'Salvar alterações' : 'Cadastrar implemento'" [loading]="salvando()" (pressed)="salvarCadastro()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-form-drawer [visible]="modo() === 'acoplar' || modo() === 'desacoplar'" (visibleChange)="!$event && fechar()"
      [title]="modo() === 'acoplar' ? (selecionado()?.acoplamentoAtual ? 'Mudar de veículo' : 'Acoplar a um veículo') : 'Desacoplar implemento'"
      [description]="descricaoMovimento()"
      [dirty]="!!idVeiculo()" [loading]="salvando()" size="sm">
      <div class="ces-form-stack">
        @if (modo() === 'acoplar' && selecionado()?.acoplamentoAtual) {
          <ces-callout tone="info" [dense]="true">
            Hoje está no veículo {{ placa(selecionado()!.acoplamentoAtual!.placa) }}. Esse acoplamento é encerrado no instante informado.
          </ces-callout>
        }
        @if (modo() === 'acoplar') {
          <ces-select label="Veículo" [required]="true" [options]="veiculosOpcoes()" [(value)]="idVeiculo" [filter]="true"
            [error]="erroCampo('veiculo')"></ces-select>
        }
        <div class="ces-form-grid">
          <ces-datepicker [label]="modo() === 'acoplar' ? 'Data do acoplamento' : 'Data da retirada'" [required]="true" [(value)]="data"
            [maxDate]="hoje"></ces-datepicker>
          <ces-text-input label="Hora" [required]="true" [(value)]="hora" placeholder="HH:mm" [maxlength]="5" [error]="erroCampo('hora')"></ces-text-input>
        </div>
        @if (modo() === 'acoplar') {
          <ces-textarea label="Observação" [(value)]="observacao" [rows]="2" [maxlength]="500" [showOptional]="true"></ces-textarea>
        }
      </div>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="fechar()"></ces-button>
        <ces-button [variant]="modo() === 'desacoplar' ? 'destructive' : 'primary'" icon="pi pi-check"
          [label]="modo() === 'acoplar' ? 'Confirmar acoplamento' : 'Confirmar retirada'" [loading]="salvando()" (pressed)="salvarMovimento()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-form-drawer [visible]="modo() === 'historico'" (visibleChange)="!$event && fechar()"
      title="Histórico de acoplamentos" [description]="(selecionado()?.identificacao ?? '') + ' — veículos em que esteve acoplado.'" size="md">
      <ces-data-table caption="Histórico de acoplamentos" [searchable]="false" [columns]="colunasHistorico" [rows]="historico()"
        [rowKey]="chaveAcoplamento" [state]="carregandoHistorico() ? 'loading' : historico().length ? 'ready' : 'empty'"
        stateTitle="Nunca acoplado" stateDescription="Os acoplamentos deste implemento aparecem aqui.">
        <ng-template cesCell="placa" let-row><span class="ces-cell-strong">{{ placa(row.placa) }}</span></ng-template>
        <ng-template cesCell="inicio" let-row>{{ row.inicio | date: 'dd/MM/yyyy HH:mm' }}</ng-template>
        <ng-template cesCell="fim" let-row>
          @if (row.fim) {
            {{ row.fim | date: 'dd/MM/yyyy HH:mm' }}
          } @else {
            <ces-status tone="success">Acoplado</ces-status>
          }
        </ng-template>
      </ces-data-table>
    </ces-form-drawer>
  `
})
export class FrotaImplementosComponent {
  private readonly frota = inject(FrotaService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  protected readonly acesso = acessoFrota();

  protected readonly tomSituacao: Record<string, CesTone> = TOM_SITUACAO;
  protected readonly rotuloSituacao: Record<string, string> = ROTULO_SITUACAO;
  protected readonly placa = formatarPlaca;
  protected readonly hoje = new Date();

  protected readonly bases = basesFrota();
  protected readonly tipos = signal<TipoImplemento[]>([]);
  protected readonly veiculos = signal<VeiculoFrotaResumo[]>([]);
  protected readonly opcoes = signal<OpcoesFrota | null>(null);

  protected readonly modo = signal<Modo | null>(null);
  protected readonly selecionado = signal<Implemento | null>(null);
  protected readonly salvando = signal(false);
  protected readonly tentouSalvar = signal(false);
  protected readonly identificacao = signal('');
  protected readonly idTipo = signal<string | null>(null);
  protected readonly idBase = signal<string | null>(null);
  protected readonly fabricante = signal('');
  protected readonly modelo = signal('');
  protected readonly numeroSerie = signal('');
  protected readonly ano = signal<number | null>(null);
  protected readonly situacao = signal<SituacaoOperacional | null>('ATIVO');
  protected readonly observacao = signal('');
  protected readonly idVeiculo = signal<string | null>(null);
  protected readonly data = signal<Date | null>(new Date());
  protected readonly hora = signal('');
  protected readonly historico = signal<AcoplamentoImplemento[]>([]);
  protected readonly carregandoHistorico = signal(false);

  protected readonly acopladoOpcoes: CesSelectOption<boolean>[] = [
    { label: 'Acoplados', value: true },
    { label: 'Livres', value: false }
  ];
  protected readonly tiposOpcoes = computed(() => this.tipos().map((t) => ({ label: t.nome, value: t.id })));
  protected readonly tiposOpcoesAtivos = computed(() =>
    this.tipos()
      .filter((t) => t.ativo || t.id === this.selecionado()?.idTipoImplemento)
      .map((t) => ({ label: t.nome, value: t.id }))
  );
  protected readonly situacoesOpcoes = computed(() => (this.opcoes()?.situacoes ?? []).map((o) => ({ label: o.rotulo, value: o.valor })));
  protected readonly veiculosOpcoes = computed(() =>
    this.veiculos()
      .filter((v) => v.situacao !== 'BAIXADO')
      .map((v) => ({ label: `${formatarPlaca(v.placa)} — ${v.modelo ?? ''} (${v.baseOperacional ?? 'sem base'})`, value: v.idVeiculo }))
  );
  protected readonly lista = cesListState({
    storageKey: 'frota-implementos',
    filters: {
      idsBase: cesFilter.list<string>([], { label: 'Base', param: 'base', itemLabel: (id) => this.bases.nome(id), countLabel: (n) => `${n} selecionadas` }),
      idsTipoImplemento: cesFilter.list<string>([], {
        label: 'Tipo',
        param: 'tipo',
        itemLabel: (id) => this.tipos().find((t) => t.id === id)?.nome ?? 'tipo selecionado'
      }),
      acoplado: cesFilter.value<boolean | null>(null, { label: 'Acoplamento', options: () => this.acopladoOpcoes })
    },
    sort: [{ key: 'identificacao', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<Implemento>(
    (pedido) => this.frota.listarImplementos(this.filtro(), paginaFrota(pedido)),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO_FROTA;
  protected readonly exportar = exportacaoFrota(
    (pagina) => this.frota.listarImplementos(this.filtro(), pagina),
    () => this.lista.request()
  );
  protected readonly descricaoTabela = computed(() =>
    this.resultado.loading() && !this.resultado.total() ? undefined : contar(this.resultado.total(), 'implemento', 'implementos', 'Nenhum implemento')
  );
  protected readonly descricaoMovimento = computed(() => {
    const i = this.selecionado();
    if (!i) return undefined;
    return this.modo() === 'acoplar'
      ? `${i.identificacao} · ${i.tipoImplemento}. Escolha o veículo e o instante em que foi acoplado.`
      : `${i.identificacao} sai do veículo ${formatarPlaca(i.acoplamentoAtual?.placa)} no instante informado e fica livre.`;
  });

  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (this.modo() === 'cadastro') {
      if (!this.identificacao().trim()) e['identificacao'] = 'Informe a identificação.';
      if (!this.idTipo()) e['tipo'] = 'Escolha o tipo.';
      if (!this.idBase()) e['base'] = 'Escolha a base.';
    } else {
      if (this.modo() === 'acoplar' && !this.idVeiculo()) e['veiculo'] = 'Escolha o veículo.';
      if (!/^([01]\d|2[0-3]):([0-5]\d)$/.test(this.hora().trim())) e['hora'] = 'Use o formato HH:mm.';
    }
    return e;
  });

  protected readonly colunas: CesDataTableColumn<Implemento>[] = [
    { key: 'identificacao', header: 'Implemento', type: 'custom', priority: 1, sortField: 'identificacao', value: (i) => i.identificacao, display: (i) => `${i.identificacao} ${i.tipoImplemento}` },
    { key: 'baseOperacional', header: 'Base', display: (i) => i.baseOperacional ?? '' },
    { key: 'situacao', header: 'Situação', type: 'custom', sortField: 'situacao', value: (i) => i.situacao, display: (i) => ROTULO_SITUACAO[i.situacao] },
    { key: 'veiculo', header: 'Veículo', type: 'custom', priority: 1, value: (i) => i.acoplamentoAtual?.placa ?? '', display: (i) => i.acoplamentoAtual?.placa ?? 'Livre' }
  ];
  protected readonly colunasHistorico: CesDataTableColumn<AcoplamentoImplemento>[] = [
    { key: 'placa', header: 'Veículo', type: 'custom', priority: 1, value: (a) => a.placa, display: (a) => a.placa ?? '' },
    { key: 'inicio', header: 'Acoplado em', type: 'custom', value: (a) => a.inicio, display: (a) => a.inicio },
    { key: 'fim', header: 'Desacoplado em', type: 'custom', value: (a) => a.fim ?? '9999', display: (a) => a.fim ?? 'Atual' }
  ];
  protected readonly chave = (i: Implemento) => i.id;
  protected readonly rotulo = (i: Implemento) => i.identificacao;
  protected readonly chaveAcoplamento = (a: AcoplamentoImplemento) => a.id;

  constructor() {
    this.frota.opcoes().subscribe({ next: (o) => this.opcoes.set(o), error: () => undefined });
    forkJoin({ tipos: this.frota.tiposImplemento(), veiculos: this.frota.listarVeiculos({ ativo: true }, PAGINA_OPCOES) }).subscribe({
      next: (r) => {
        this.tipos.set(r.tipos.content);
        this.veiculos.set(r.veiculos.content);
      },
      error: () => undefined
    });
  }

  private filtro() {
    const pedido = this.lista.request();
    return { ...pedido.filters, busca: pedido.query || null };
  }

  protected erroCampo(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected fechar(): void {
    this.modo.set(null);
  }

  protected abrirCadastro(implemento: Implemento | null): void {
    this.selecionado.set(implemento);
    this.tentouSalvar.set(false);
    this.identificacao.set(implemento?.identificacao ?? '');
    this.idTipo.set(implemento?.idTipoImplemento ?? null);
    this.idBase.set(implemento?.idBaseOperacional ?? null);
    this.fabricante.set(implemento?.fabricante ?? '');
    this.modelo.set(implemento?.modelo ?? '');
    this.numeroSerie.set(implemento?.numeroSerie ?? '');
    this.ano.set(implemento?.anoFabricacao ?? null);
    this.situacao.set(implemento?.situacao ?? 'ATIVO');
    this.observacao.set(implemento?.observacao ?? '');
    this.modo.set('cadastro');
  }

  protected abrir(modo: 'acoplar' | 'desacoplar', implemento: Implemento): void {
    const agora = new Date();
    this.selecionado.set(implemento);
    this.tentouSalvar.set(false);
    this.idVeiculo.set(null);
    this.observacao.set('');
    this.data.set(agora);
    this.hora.set(`${String(agora.getHours()).padStart(2, '0')}:${String(agora.getMinutes()).padStart(2, '0')}`);
    this.modo.set(modo);
  }

  protected salvarCadastro(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const texto = (v: string) => (v.trim() ? v.trim() : null);
    const request: ImplementoRequest = {
      identificacao: this.identificacao().trim(),
      idTipoImplemento: this.idTipo()!,
      idBaseOperacional: this.idBase()!,
      fabricante: texto(this.fabricante()),
      modelo: texto(this.modelo()),
      numeroSerie: texto(this.numeroSerie()),
      anoFabricacao: this.ano(),
      situacao: this.selecionado() ? this.situacao() : null,
      observacao: texto(this.observacao())
    };
    this.salvando.set(true);
    this.frota.salvarImplemento(request, this.selecionado()?.id).subscribe({
      next: () => this.concluir('Implemento salvo.'),
      error: (e) => this.falhar(e)
    });
  }

  protected salvarMovimento(): void {
    this.tentouSalvar.set(true);
    const implemento = this.selecionado();
    const data = this.data();
    if (Object.keys(this.erros()).length || !implemento || !data) return;
    const [h, m] = this.hora().trim().split(':').map(Number);
    const instante = new Date(data);
    instante.setHours(h, m, 0, 0);
    this.salvando.set(true);
    const operacao =
      this.modo() === 'acoplar'
        ? this.frota.acoplarImplemento(implemento.id, {
            idVeiculo: this.idVeiculo()!,
            inicio: paraInstanteIso(instante),
            observacao: this.observacao().trim() || null
          })
        : this.frota.desacoplarImplemento(implemento.id, paraInstanteIso(instante));
    operacao.subscribe({
      next: (a) =>
        this.concluir(this.modo() === 'acoplar' ? `${a.identificacao} acoplado em ${formatarPlaca(a.placa)}.` : `${a.identificacao} desacoplado.`),
      error: (e) => this.falhar(e)
    });
  }

  protected async alternar(implemento: Implemento): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: implemento.ativo ? 'Desativar implemento' : 'Reativar implemento',
      message: implemento.ativo
        ? `${implemento.identificacao} sai das listas. O histórico é mantido.`
        : `${implemento.identificacao} volta a aparecer nas listas.`,
      confirmLabel: implemento.ativo ? 'Desativar' : 'Reativar',
      variant: implemento.ativo ? 'destructive' : 'default'
    });
    if (!ok) return;
    this.frota.alternarImplemento(implemento).subscribe({
      next: () => {
        this.notificacoes.success(implemento.ativo ? 'Implemento desativado.' : 'Implemento reativado.');
        this.modo.set(null);
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected abrirHistorico(implemento: Implemento): void {
    this.selecionado.set(implemento);
    this.historico.set([]);
    this.carregandoHistorico.set(true);
    this.modo.set('historico');
    this.frota.historicoImplemento(implemento.id).subscribe({
      next: (h) => {
        this.historico.set(h);
        this.carregandoHistorico.set(false);
      },
      error: (e) => {
        this.carregandoHistorico.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  private concluir(mensagem: string): void {
    this.salvando.set(false);
    this.modo.set(null);
    this.notificacoes.success(mensagem);
    this.lista.reload();
  }

  private falhar(erro: unknown): void {
    this.salvando.set(false);
    this.notificacoes.error(getErrorMessage(erro));
  }
}
