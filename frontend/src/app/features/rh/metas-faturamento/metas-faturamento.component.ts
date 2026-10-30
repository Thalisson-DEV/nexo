import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
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
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import {
  MetaFaturamentoResponse,
  PERIODICIDADE_FATURAMENTO_LABELS,
  PeriodicidadeMetaFaturamento,
  competenciaDaData,
  competenciaDe,
  competencia as montarCompetencia,
  diasUteisDaJanela,
  formatarDataCurta,
  janelaDaCompetencia,
  nomeCompetencia,
  processoUsaCiclo
} from '../../../core/models/meta-faturamento.models';
import { BaseOperacionalResponse, EquipeResponse, ProcessoNegocioResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { MetasFaturamentoService } from '../../../core/services/metas-faturamento.service';
import { RhService } from '../../../core/services/rh.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { OPCOES_STATUS, dataIso, dataLocal, paginaPadrao } from '../../../shared/listagem/listagem';

const PERIODICIDADES: CesSelectOption<PeriodicidadeMetaFaturamento>[] = [
  { label: PERIODICIDADE_FATURAMENTO_LABELS.CICLO_COMERCIAL, value: 'CICLO_COMERCIAL' },
  { label: PERIODICIDADE_FATURAMENTO_LABELS.MENSAL, value: 'MENSAL' }
];
const PERIODICIDADES_FILTRO: CesSelectOption<PeriodicidadeMetaFaturamento | null>[] = [{ label: 'Todas', value: null }, ...PERIODICIDADES];
const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
/** Competências oferecidas no formulário: um ano para trás e um ano e meio à frente. */
const MESES_ANTES = 12;
const MESES_DEPOIS = 18;

function nomeEm(lista: { id: string; nome: string }[], id: string): string {
  return lista.find((item) => item.id === id)?.nome ?? 'selecionado';
}

@Component({
  selector: 'app-metas-faturamento',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
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
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Metas de faturamento"
        icon="pi pi-flag"
        [breadcrumb]="[{ label: 'Comercial' }, { label: 'Metas de faturamento' }]"
        description="Quanto cada equipe deve faturar por ciclo comercial (26 a 25) ou por mês."
        helpLabel="Ajuda sobre metas de faturamento"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Nova meta" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="rh-metas-faturamento" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Equipe" [options]="equipesOpcoes()" [(value)]="lista.filters.idEquipe" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Base operacional" [options]="basesOpcoes()" [(value)]="lista.filters.idBaseOperacional" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Processo" [options]="processosOpcoes()" [(value)]="lista.filters.idProcesso" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Periodicidade" [options]="periodicidadesFiltro" [(value)]="lista.filters.periodicidade"></ces-select>
          <ces-datepicker label="Vigente em" [value]="dataLocal(lista.filters.vigenteEm())" [clearable]="true" (valueChange)="lista.filters.vigenteEm.set(dataIso($event))"></ces-datepicker>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Metas"
        mode="server"
        storageKey="rh-metas-faturamento"
        searchPlaceholder="Descrição da meta"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova meta"
        noResultsTitle="Nenhuma meta encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as metas' : 'Nenhuma meta cadastrada'"
        [stateDescription]="resultado.error() ?? 'Defina quanto cada equipe deve faturar por ciclo ou por mês.'"
        [filtered]="lista.filtered()"
        [(page)]="lista.page"
        [(pageSize)]="lista.pageSize"
        [(sort)]="lista.sort"
        [(query)]="lista.query"
        (lazyLoad)="lista.load($event)"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="novo()"
        (rowActivate)="editar($event)"
      >
        <ng-template cesCell="equipe" let-row>
          <span class="ces-cell-strong">{{ row.codigoEquipe ?? 'Equipe não encontrada' }}</span>
          <span class="ces-cell-muted">{{ detalheEquipe(row) }}</span>
        </ng-template>
        <ng-template cesCell="meta" let-row>
          <span class="ces-cell-strong">{{ moeda(row.valor) }}</span>
          <span class="ces-cell-muted">{{ rotuloPeriodicidade(row.periodicidade) }}</span>
        </ng-template>
        <ng-template cesCell="vigencia" let-row>
          {{ vigencia(row) }}
          @if (row.descricao) {
            <span class="ces-cell-muted">{{ row.descricao }}</span>
          }
        </ng-template>
        <ng-template cesCell="janela" let-row>
          @if (row.janelaAtualInicio && row.janelaAtualFim) {
            {{ formatarData(row.janelaAtualInicio) }} a {{ formatarData(row.janelaAtualFim) }}
            <span class="ces-cell-muted">{{ moeda(row.metaDiariaAtual) }} por dia útil ({{ row.diasUteisJanelaAtual }} dias)</span>
          } @else {
            <span class="ces-cell-muted">Fora da vigência</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar meta de ' : 'Reativar meta de ') + (row.codigoEquipe ?? 'equipe')"
            size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar meta' : 'Nova meta'" [description]="emEdicao()?.codigoEquipe ?? undefined"
      [dirty]="alterado()" size="md">
      <ces-form-section title="Equipes e valor" icon="pi pi-users">
        @if (emEdicao(); as meta) {
          <ces-callout tone="info">A equipe não muda. Para outra equipe, cadastre outra meta.</ces-callout>
          <ces-text-input label="Equipe" [value]="meta.codigoEquipe ?? ''" [readonly]="true"></ces-text-input>
        } @else {
          <ces-multiselect label="Equipes" [required]="true" [options]="equipesOpcoes()" [value]="idsEquipes()" (valueChange)="escolherEquipes($event)"
            display="chip" [maxSelectedLabels]="6" selectedItemsLabel="{0} equipes" [loading]="carregandoEquipes()"
            description="A mesma meta é criada para cada equipe escolhida." [error]="erro('equipes')"></ces-multiselect>
        }
        <ces-number-input label="Meta por janela" mode="currency" currency="BRL" locale="pt-BR" [min]="0" [required]="true"
          [(value)]="valor" [description]="'Faturamento esperado em cada ' + (periodicidade() === 'CICLO_COMERCIAL' ? 'ciclo.' : 'mês.')" [error]="erro('valor')"></ces-number-input>
      </ces-form-section>

      <ces-form-section title="Período" icon="pi pi-calendar">
        <ces-select label="Apuração" [required]="true" [options]="periodicidades" [value]="periodicidade()" (valueChange)="escolherPeriodicidade($event)"
          description="Equipes do processo comercial apuram pelo ciclo de 26 a 25."></ces-select>
        @if (processosMisturados()) {
          <ces-callout tone="warning">As equipes escolhidas são de processos diferentes. Confira a apuração: o ciclo de 26 a 25 vale só para o comercial.</ces-callout>
        }
        <div class="ces-form-grid">
          <ces-select label="A partir de" [required]="true" [options]="competenciasOpcoes()" [(value)]="compInicio" [filter]="true" [error]="erro('inicio')"></ces-select>
          <ces-select label="Até" [options]="competenciasFimOpcoes()" [(value)]="compFim" [filter]="true" [clearable]="true" [showOptional]="true"
            placeholder="Sem prazo" [error]="erro('fim')"></ces-select>
        </div>
        <ces-text-input label="Descrição" [(value)]="descricao" [maxlength]="200" [showOptional]="true" placeholder="Ex.: Meta do 2º semestre"></ces-text-input>
        @if (!emEdicao()) {
          <ces-boolean-control label="Substituir a meta atual das equipes" [(value)]="substituir"
            description="A meta sem prazo que a equipe já tem termina no fim do período anterior. O histórico continua valendo nos relatórios."></ces-boolean-control>
        }
        @if (previa(); as p) {
          <ces-callout tone="info" title="Prévia">{{ p }}</ces-callout>
        }
        @if (erroServidor(); as mensagem) {
          <ces-callout tone="danger">{{ mensagem }}</ces-callout>
        }
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : rotuloCadastrar()" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda titulo="Metas de faturamento" artigoPlural="as" entidadePlural="metas" busca="descrição" rotuloNovo="Nova meta">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-calendar"></i> Ciclo comercial</h4>
        <p class="help-text-desc">As equipes do processo comercial apuram a meta do dia 26 de um mês ao dia 25 do seguinte. O ciclo leva o nome do mês em que termina: 26/09 a 25/10 é o ciclo de outubro. As demais equipes usam o mês civil.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-refresh"></i> Reajustar a meta</h4>
        <p class="help-text-desc">Cadastre a nova meta a partir do ciclo desejado com "Substituir a meta atual". A meta anterior termina no ciclo de antes e continua valendo para os relatórios do passado.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-chart-line"></i> Dashboards e app de campo</h4>
        <p class="help-text-desc">O dashboard pode ser filtrado por mês: a meta de cada dia útil vem do ciclo que contém aquele dia. No app de campo, a equipe acompanha quanto já faturou no ciclo, quanto falta e o ritmo diário necessário.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class MetasFaturamentoComponent {
  private readonly metas = inject(MetasFaturamentoService);
  private readonly rh = inject(RhService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  protected readonly periodicidades = PERIODICIDADES;
  protected readonly periodicidadesFiltro = PERIODICIDADES_FILTRO;
  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly formatarData = formatarDataCurta;
  protected readonly dataLocal = dataLocal;
  protected readonly dataIso = dataIso;

  protected readonly equipes = signal<EquipeResponse[]>([]);
  protected readonly carregandoEquipes = signal(true);
  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly equipesOpcoes = computed<CesSelectOption<string>[]>(() =>
    this.equipes().map((e) => ({ label: [e.codigo, e.processo?.nome].filter(Boolean).join(' · '), value: e.id }))
  );
  protected readonly basesOpcoes = computed(() => this.bases().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly processosOpcoes = computed(() => this.processos().map((p) => ({ label: p.nome, value: p.id })));

  protected readonly lista = cesListState({
    storageKey: 'rh-metas-faturamento',
    filters: {
      idEquipe: cesFilter.value<string | null>(null, {
        label: 'Equipe',
        param: 'equipe',
        format: (id): string | null => (id ? (this.equipes().find((e) => e.id === id)?.codigo ?? 'equipe selecionada') : null)
      }),
      idBaseOperacional: cesFilter.value<string | null>(null, { label: 'Base', param: 'base', format: (id): string | null => (id ? nomeEm(this.bases(), id) : null) }),
      idProcesso: cesFilter.value<string | null>(null, { label: 'Processo', param: 'processo', format: (id): string | null => (id ? nomeEm(this.processos(), id) : null) }),
      periodicidade: cesFilter.value<PeriodicidadeMetaFaturamento | null>(null, { label: 'Apuração', type: 'string', options: () => PERIODICIDADES_FILTRO }),
      vigenteEm: cesFilter.date(null, { label: 'Vigente em', param: 'vigente' }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'vigencia', direction: 'desc' }]
  });
  protected readonly resultado = this.lista.results<MetaFaturamentoResponse>(
    (pedido) => {
      const { filters, query } = this.lista.request();
      return this.metas.listar({ ...filters, buscaGlobal: query || null, ...paginaPadrao(pedido) });
    },
    { errorMessage: getErrorMessage }
  );

  protected readonly colunas: CesDataTableColumn<MetaFaturamentoResponse>[] = [
    { key: 'equipe', header: 'Equipe', type: 'custom', priority: 1, value: (m) => m.codigoEquipe, display: (m) => m.codigoEquipe ?? '—' },
    { key: 'meta', header: 'Meta', type: 'custom', priority: 1, sortField: 'valor', value: (m) => m.valor, display: (m) => MOEDA.format(m.valor) },
    { key: 'vigencia', header: 'Vigência', type: 'custom', sortField: 'vigenciaInicio', value: (m) => m.vigenciaInicio, display: (m) => this.vigencia(m) },
    { key: 'janela', header: 'Janela atual', type: 'custom', value: (m) => m.janelaAtualInicio },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (m) => m.ativo, display: (m) => (m.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (m: MetaFaturamentoResponse) => m.codigoEquipe ?? 'Meta';

  // Formulário
  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<MetaFaturamentoResponse | null>(null);
  protected readonly idsEquipes = signal<string[]>([]);
  protected readonly valor = signal<number | null>(null);
  protected readonly periodicidade = signal<PeriodicidadeMetaFaturamento>('CICLO_COMERCIAL');
  protected readonly compInicio = signal<string | null>(null);
  protected readonly compFim = signal<string | null>(null);
  protected readonly descricao = signal('');
  protected readonly substituir = signal(true);
  protected readonly salvando = signal(false);
  protected readonly erroServidor = signal<string | null>(null);
  private readonly tentouSalvar = signal(false);
  private readonly fotografia = signal('');
  /** A apuração segue o processo das equipes até a pessoa escolhê-la à mão. */
  private readonly apuracaoManual = signal(false);

  protected readonly competenciasOpcoes = computed<CesSelectOption<string>[]>(() => {
    const p = this.periodicidade();
    const atual = competenciaDe(p, new Date());
    const [ano, mes] = atual.split('-').map(Number);
    const opcoes: CesSelectOption<string>[] = [];
    for (let i = -MESES_ANTES; i <= MESES_DEPOIS; i++) {
      const comp = montarCompetencia(ano, mes + i);
      const janela = janelaDaCompetencia(p, comp);
      const sufixo = comp === atual ? ' · atual' : '';
      opcoes.push({ label: `${nomeCompetencia(p, comp)} (${formatarDataCurta(janela.inicio)} a ${formatarDataCurta(janela.fim)})${sufixo}`, value: comp });
    }
    const escolhida = this.compInicio();
    if (escolhida && !opcoes.some((o) => o.value === escolhida)) opcoes.unshift(this.opcaoCompetencia(escolhida));
    return opcoes;
  });
  protected readonly competenciasFimOpcoes = computed(() => {
    const inicio = this.compInicio();
    const opcoes = this.competenciasOpcoes().filter((o) => !inicio || o.value >= inicio);
    const escolhida = this.compFim();
    return escolhida && !opcoes.some((o) => o.value === escolhida) ? [this.opcaoCompetencia(escolhida), ...opcoes] : opcoes;
  });

  protected readonly processosMisturados = computed(() => {
    const escolhidas = this.equipes().filter((e) => this.idsEquipes().includes(e.id));
    const usaCiclo = new Set(escolhidas.map((e) => processoUsaCiclo(e.processo?.nome)));
    return usaCiclo.size > 1;
  });

  protected readonly rotuloCadastrar = computed(() => {
    const n = this.idsEquipes().length;
    return n > 1 ? `Cadastrar ${n} metas` : 'Cadastrar meta';
  });

  protected readonly previa = computed(() => {
    const valor = this.valor();
    const inicio = this.compInicio();
    if (!valor || valor <= 0 || !inicio) return null;
    const p = this.periodicidade();
    const janela = janelaDaCompetencia(p, inicio);
    const uteis = diasUteisDaJanela(janela);
    const fim = this.compFim();
    const ate = fim ? ` até ${nomeCompetencia(p, fim).toLowerCase()} (${formatarDataCurta(janelaDaCompetencia(p, fim).fim)})` : ', sem prazo';
    const diaria = uteis ? ` Em ${nomeCompetencia(p, inicio).toLowerCase()} são ${uteis} dias úteis: ${MOEDA.format(valor / uteis)} por dia.` : '';
    return `${MOEDA.format(valor)} por ${p === 'CICLO_COMERCIAL' ? 'ciclo' : 'mês'} a partir de ${formatarDataCurta(janela.inicio)}${ate}.${diaria}`;
  });

  protected readonly alterado = computed(() => this.formularioAberto() && this.estadoFormulario() !== this.fotografia());
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.emEdicao() && !this.idsEquipes().length) e['equipes'] = 'Escolha ao menos uma equipe.';
    const valor = this.valor();
    if (!valor || valor <= 0) e['valor'] = 'Informe um valor maior que zero.';
    if (!this.compInicio()) e['inicio'] = 'Escolha o início da vigência.';
    const fim = this.compFim();
    if (fim && this.compInicio() && fim < this.compInicio()!) e['fim'] = 'O fim não pode ser anterior ao início.';
    return e;
  });

  constructor() {
    const ignorar = () => undefined;
    this.rh.listarEquipes({ ativo: true, page: 0, size: 500, sort: 'codigo,asc' }).subscribe({
      next: (r) => {
        this.equipes.set(r.content);
        this.carregandoEquipes.set(false);
      },
      error: (e) => {
        this.carregandoEquipes.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
    this.rh.listarBasesOperacionaisAtivas().subscribe({ next: (r) => this.bases.set(r.content), error: ignorar });
    this.rh.listarProcessosAtivos().subscribe({ next: (r) => this.processos.set(r.content ?? []), error: ignorar });
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.emEdicao.set(null);
    this.idsEquipes.set([]);
    this.valor.set(null);
    this.periodicidade.set('CICLO_COMERCIAL');
    this.apuracaoManual.set(false);
    this.compInicio.set(competenciaDe('CICLO_COMERCIAL', new Date()));
    this.compFim.set(null);
    this.descricao.set('');
    this.substituir.set(true);
    this.abrir();
  }

  protected editar(meta: MetaFaturamentoResponse): void {
    this.emEdicao.set(meta);
    this.idsEquipes.set([meta.idEquipe]);
    this.valor.set(meta.valor);
    this.periodicidade.set(meta.periodicidade);
    this.apuracaoManual.set(true);
    this.compInicio.set(competenciaDaData(meta.periodicidade, meta.vigenciaInicio));
    this.compFim.set(meta.vigenciaFim ? competenciaDaData(meta.periodicidade, meta.vigenciaFim) : null);
    this.descricao.set(meta.descricao ?? '');
    this.abrir();
  }

  private abrir(): void {
    this.tentouSalvar.set(false);
    this.erroServidor.set(null);
    this.fotografia.set(this.estadoFormulario());
    this.formularioAberto.set(true);
  }

  /** A apuração acompanha o processo das equipes: comercial → ciclo; demais → mês. */
  protected escolherEquipes(ids: string[]): void {
    this.idsEquipes.set(ids);
    if (this.apuracaoManual() || !ids.length) return;
    const escolhidas = this.equipes().filter((e) => ids.includes(e.id));
    const todasCiclo = escolhidas.every((e) => processoUsaCiclo(e.processo?.nome));
    const nenhumaCiclo = escolhidas.every((e) => !processoUsaCiclo(e.processo?.nome));
    if (todasCiclo) this.periodicidade.set('CICLO_COMERCIAL');
    else if (nenhumaCiclo) this.periodicidade.set('MENSAL');
  }

  /** As competências mantêm o mês; só a janela muda (ciclo de outubro ↔ outubro civil). */
  protected escolherPeriodicidade(p: PeriodicidadeMetaFaturamento | null): void {
    if (!p) return;
    this.apuracaoManual.set(true);
    this.periodicidade.set(p);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const p = this.periodicidade();
    const vigenciaInicio = janelaDaCompetencia(p, this.compInicio()!).inicio;
    const fim = this.compFim();
    const vigenciaFim = fim ? janelaDaCompetencia(p, fim).fim : null;
    const descricao = this.descricao().trim() || null;
    const atual = this.emEdicao();
    this.salvando.set(true);
    this.erroServidor.set(null);
    const pedido: Observable<unknown> = atual
      ? this.metas.atualizar(atual.id, { valor: this.valor()!, periodicidade: p, vigenciaInicio, vigenciaFim, descricao })
      : this.metas.criar({
          idsEquipes: this.idsEquipes(),
          valor: this.valor()!,
          periodicidade: p,
          vigenciaInicio,
          vigenciaFim,
          descricao,
          substituirVigentes: this.substituir()
        });
    pedido.subscribe({
      next: (salvo) => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        const n = this.idsEquipes().length;
        this.notificacoes.success(
          salvo === null
            ? 'Sem conexão: a meta será enviada quando a internet voltar.'
            : atual
              ? 'Meta atualizada.'
              : n > 1
                ? `${n} metas cadastradas.`
                : 'Meta cadastrada.'
        );
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.erroServidor.set(getErrorMessage(e));
      }
    });
  }

  protected async alternar(meta: MetaFaturamentoResponse): Promise<void> {
    const desativar = meta.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar meta' : 'Reativar meta',
      message: desativar
        ? 'A meta sai dos dashboards e do app de campo, mas continua listada e pode ser reativada.'
        : 'A meta volta a valer nos dashboards e no app de campo.',
      confirmLabel: desativar ? 'Desativar' : 'Reativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.metas.desativar(meta.id) : this.metas.ativar(meta.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Meta desativada.' : 'Meta reativada.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected moeda(valor: number | null | undefined): string {
    return valor === null || valor === undefined ? '—' : MOEDA.format(valor);
  }

  protected detalheEquipe(m: MetaFaturamentoResponse): string {
    return [m.processo, m.baseOperacional].filter(Boolean).join(' · ');
  }

  protected rotuloPeriodicidade(p: PeriodicidadeMetaFaturamento): string {
    return PERIODICIDADE_FATURAMENTO_LABELS[p];
  }

  protected vigencia(m: MetaFaturamentoResponse): string {
    const inicio = nomeCompetencia(m.periodicidade, competenciaDaData(m.periodicidade, m.vigenciaInicio));
    const fim = m.vigenciaFim ? nomeCompetencia(m.periodicidade, competenciaDaData(m.periodicidade, m.vigenciaFim)).toLowerCase() : 'sem prazo';
    return `${inicio} → ${fim}`;
  }

  private opcaoCompetencia(comp: string): CesSelectOption<string> {
    const p = this.periodicidade();
    const janela = janelaDaCompetencia(p, comp);
    return { label: `${nomeCompetencia(p, comp)} (${formatarDataCurta(janela.inicio)} a ${formatarDataCurta(janela.fim)})`, value: comp };
  }

  private estadoFormulario(): string {
    return JSON.stringify([this.idsEquipes(), this.valor(), this.periodicidade(), this.compInicio(), this.compFim(), this.descricao(), this.substituir()]);
  }
}
