import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';

import { ChamadoPrioridade, EquipeChamado, TopicoChamado, TopicoChamadoRequest } from '../../../../core/models/chamado.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { ChamadosService } from '../../../../core/services/chamados.service';
import { RemoteLookup } from '../../../../core/utils/remote-lookup.util';
import { AjudaListagemComponent } from '../../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../../shared/listagem/listagem';
import { CHAMADO_PRIORIDADE_LABELS, PRIORIDADE_OPTIONS } from '../../chamados-ui';

const SITUACOES: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Ativos', value: true },
  { label: 'Inativos', value: false }
];

@Component({
  selector: 'app-chamados-topicos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesNumberInputComponent,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="abrir()">
      <ces-page-header
        title="Tópicos de chamados"
        icon="pi pi-tags"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Chamados' }, { label: 'Tópicos' }]"
        description="Assuntos oferecidos na abertura de chamados, com a equipe, a prioridade e os prazos (SLA) de cada um."
        helpLabel="Ajuda sobre tópicos de chamados"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Novo tópico" [keyShortcut]="atalhos.newShortcut()" (pressed)="abrir()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="chamados-topicos" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-text-input label="Código" [value]="lista.filters.codigo() ?? ''" (valueChange)="lista.filters.codigo.set($event || null)" placeholder="Ex.: SISTEMA_ERRO"></ces-text-input>
          <ces-select label="Equipe padrão" [options]="equipesOpcoes()" [(value)]="lista.filters.equipePadraoId" [filter]="true" [clearable]="true"
            placeholder="Todas as equipes" [loading]="equipeLookup.loading()" (opened)="equipeLookup.open()" (search)="equipeLookup.search($event)"></ces-select>
          <ces-select label="Status" [options]="situacoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Tópicos"
        mode="server"
        storageKey="chamados-topicos"
        exportFileName="topicos-chamados"
        searchPlaceholder="Nome, código ou descrição"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo tópico"
        noResultsTitle="Nenhum tópico encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os tópicos' : 'Nenhum tópico ativo'"
        [stateDescription]="resultado.error() ?? 'Cadastre o primeiro tópico ou remova o status nos filtros para ver os inativos.'"
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
        (emptyAction)="abrir()"
        (rowActivate)="abrir($event)"
      >
        <ng-template cesCell="nome" let-row>
          <span class="ces-cell-strong">{{ row.nome }}</span>
          <code class="ces-cell-muted">{{ row.codigo }}</code>
        </ng-template>
        <ng-template cesCell="prioridade" let-row>
          <ces-status tone="info" emphasis="outline">{{ prioridade(row.prioridadePadrao) }}</ces-status>
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar tópico' : 'Novo tópico'" [description]="emEdicao()?.nome"
      [dirty]="alterado()" size="md">
      <ces-form-section title="Tópico" icon="pi pi-tags">
        <div class="ces-form-grid">
          <ces-text-input label="Código" [required]="true" [value]="codigo()" (valueChange)="codigo.set($event.toUpperCase())" [maxlength]="80"
            placeholder="SISTEMA_ERRO" [error]="erro('codigo')"></ces-text-input>
          <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="150" placeholder="Erro em sistema" [error]="erro('nome')"></ces-text-input>
        </div>
        <ces-textarea label="Descrição" [(value)]="descricao" [rows]="3" [maxlength]="1000" [showOptional]="true"></ces-textarea>
        <div class="ces-form-grid">
          <ces-select label="Equipe padrão" [required]="true" [options]="equipesOpcoes()" [(value)]="equipePadraoId" [filter]="true"
            [loading]="equipeLookup.loading()" (opened)="equipeLookup.open()" (search)="equipeLookup.search($event)" [error]="erro('equipe')"></ces-select>
          <ces-select label="Prioridade padrão" [required]="true" [options]="prioridades" [(value)]="prioridadePadrao"></ces-select>
          <ces-number-input label="Ordem de exibição" [required]="true" [(value)]="ordemExibicao" [min]="0" [useGrouping]="false"></ces-number-input>
        </div>
      </ces-form-section>
      <ces-form-section title="Prazos (SLA)" icon="pi pi-clock">
        <div class="ces-form-grid">
          <ces-number-input label="Primeira resposta (horas)" [required]="true" [(value)]="slaPrimeiraResposta" [min]="1" [useGrouping]="false" [error]="erro('sla1')"></ces-number-input>
          <ces-number-input label="Resolução (horas)" [required]="true" [(value)]="slaResolucao" [min]="1" [useGrouping]="false" [error]="erro('sla2')"></ces-number-input>
          <ces-number-input label="Prazo para reabertura (horas)" [required]="true" [(value)]="prazoReabertura" [min]="1" [useGrouping]="false"></ces-number-input>
        </div>
        <ces-boolean-control label="Pausar o SLA de resolução enquanto aguarda o solicitante" [(value)]="pausarSla"></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar tópico'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda titulo="Tópicos de chamados" entidadePlural="tópicos" busca="nome, código ou descrição" rotuloNovo="Novo tópico">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-clock"></i> Prazos</h4>
        <p class="help-text-desc">O prazo de primeira resposta não pode passar do prazo de resolução. Desativar tira o tópico das novas aberturas.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class ChamadosTopicosComponent {
  private readonly servico = inject(ChamadosService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  protected readonly situacoes = SITUACOES;
  protected readonly prioridades = PRIORIDADE_OPTIONS as CesSelectOption<ChamadoPrioridade>[];
  protected readonly lista = cesListState({
    storageKey: 'chamados-topicos',
    filters: {
      codigo: cesFilter.value<string | null>(null, { label: 'Código' }),
      equipePadraoId: cesFilter.value<string | null>(null, { label: 'Equipe', param: 'equipe', format: (id): string | null => (id ? this.nomeEquipe(id) : null) }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', options: () => SITUACOES })
    },
    sort: [{ key: 'ordemExibicao', direction: 'asc' }]
  });

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<TopicoChamado | null>(null);
  protected readonly codigo = signal('');
  protected readonly nome = signal('');
  protected readonly descricao = signal('');
  protected readonly equipePadraoId = signal<string | null>(null);
  protected readonly prioridadePadrao = signal<ChamadoPrioridade | null>('NORMAL');
  protected readonly ordemExibicao = signal<number | null>(10);
  protected readonly slaPrimeiraResposta = signal<number | null>(8);
  protected readonly slaResolucao = signal<number | null>(24);
  protected readonly prazoReabertura = signal<number | null>(72);
  protected readonly pausarSla = signal(true);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);

  protected readonly equipeLookup = new RemoteLookup<EquipeChamado>(
    {
      identify: (equipe) => equipe.id,
      selectedKeys: (): string[] => [this.lista.filters.equipePadraoId() as string | null, this.equipePadraoId()].filter((id): id is string => !!id),
      load: ({ query, page, size }) => this.servico.listarEquipes({ ativo: true, buscaGlobal: query, page, size, sort: 'nome,asc' })
    },
    inject(DestroyRef)
  );
  protected readonly equipesOpcoes = computed<CesSelectOption<string>[]>(() => this.equipeLookup.items().map((e) => ({ label: e.nome, value: e.id })));

  protected readonly resultado = this.lista.results<TopicoChamado>(
    (pedido) => this.servico.listarTopicos({ ...this.filtro(), ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string }) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao(
    (pagina) => this.servico.listarTopicos({ ...this.filtro(), ...(pagina as { page: number; size: number; sort?: string }) }),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<TopicoChamado>[] = [
    { key: 'nome', header: 'Tópico', type: 'custom', priority: 1, sortField: 'nome', value: (t) => t.nome, display: (t) => `${t.nome} (${t.codigo})` },
    { key: 'equipe', header: 'Equipe padrão', value: (t) => t.equipePadrao?.nome, display: (t) => t.equipePadrao?.nome ?? '—' },
    { key: 'prioridade', header: 'Prioridade', type: 'custom', value: (t) => t.prioridadePadrao, display: (t) => this.prioridade(t.prioridadePadrao) },
    { key: 'slaPrimeiraRespostaHoras', header: '1ª resposta', display: (t) => `${t.slaPrimeiraRespostaHoras} h` },
    { key: 'slaResolucaoHoras', header: 'Resolução', display: (t) => `${t.slaResolucaoHoras} h` },
    { key: 'prazoReaberturaHoras', header: 'Reabertura', display: (t) => `${t.prazoReaberturaHoras} h` },
    { key: 'ordemExibicao', header: 'Ordem', type: 'integer', sortField: 'ordemExibicao' },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, value: (t) => t.ativo, display: (t) => (t.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (t: TopicoChamado) => t.nome;
  protected readonly alterado = computed(() => {
    const t = this.emEdicao();
    if (!t) return !!(this.codigo() + this.nome() + this.descricao()).trim() || !!this.equipePadraoId();
    return (
      this.codigo() !== t.codigo || this.nome() !== t.nome || this.descricao() !== (t.descricao ?? '') ||
      this.equipePadraoId() !== t.equipePadrao?.id || this.prioridadePadrao() !== t.prioridadePadrao ||
      this.ordemExibicao() !== t.ordemExibicao || this.slaPrimeiraResposta() !== t.slaPrimeiraRespostaHoras ||
      this.slaResolucao() !== t.slaResolucaoHoras || this.prazoReabertura() !== t.prazoReaberturaHoras ||
      this.pausarSla() !== t.pausarSlaAguardandoSolicitante
    );
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.codigo().trim()) e['codigo'] = 'Informe o código.';
    if (!this.nome().trim()) e['nome'] = 'Informe o nome.';
    if (!this.equipePadraoId()) e['equipe'] = 'Selecione a equipe.';
    const s1 = this.slaPrimeiraResposta() ?? 0;
    const s2 = this.slaResolucao() ?? 0;
    if (s1 < 1) e['sla1'] = 'Informe o prazo de primeira resposta.';
    if (s2 < 1) e['sla2'] = 'Informe o prazo de resolução.';
    else if (s1 > s2) e['sla1'] = 'A primeira resposta não pode passar do prazo de resolução.';
    return e;
  });

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      buscaGlobal: query || undefined,
      codigo: filters.codigo?.trim() || undefined,
      equipePadraoId: filters.equipePadraoId || undefined,
      ativo: filters.ativo ?? undefined
    };
  }

  private nomeEquipe(id: string): string {
    return this.equipeLookup.items().find((e) => e.id === id)?.nome ?? 'equipe selecionada';
  }

  protected prioridade(valor: ChamadoPrioridade): string {
    return CHAMADO_PRIORIDADE_LABELS[valor];
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected abrir(t?: TopicoChamado): void {
    if (t?.equipePadrao) this.equipeLookup.setSeed([t.equipePadrao as EquipeChamado]);
    this.emEdicao.set(t ?? null);
    this.codigo.set(t?.codigo ?? '');
    this.nome.set(t?.nome ?? '');
    this.descricao.set(t?.descricao ?? '');
    this.equipePadraoId.set(t?.equipePadrao?.id ?? null);
    this.prioridadePadrao.set(t?.prioridadePadrao ?? 'NORMAL');
    this.ordemExibicao.set(t?.ordemExibicao ?? 10);
    this.slaPrimeiraResposta.set(t?.slaPrimeiraRespostaHoras ?? 8);
    this.slaResolucao.set(t?.slaResolucaoHoras ?? 24);
    this.prazoReabertura.set(t?.prazoReaberturaHoras ?? 72);
    this.pausarSla.set(t?.pausarSlaAguardandoSolicitante ?? true);
    this.tentouSalvar.set(false);
    this.formularioAberto.set(true);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request: TopicoChamadoRequest = {
      codigo: this.codigo().trim().toUpperCase(),
      nome: this.nome().trim(),
      descricao: this.descricao().trim(),
      prioridadePadrao: this.prioridadePadrao() ?? 'NORMAL',
      equipePadraoId: this.equipePadraoId()!,
      slaPrimeiraRespostaHoras: this.slaPrimeiraResposta()!,
      slaResolucaoHoras: this.slaResolucao()!,
      pausarSlaAguardandoSolicitante: this.pausarSla(),
      prazoReaberturaHoras: this.prazoReabertura() ?? 72,
      ordemExibicao: this.ordemExibicao() ?? 0
    };
    this.salvando.set(true);
    (atual ? this.servico.atualizarTopico(atual.id, request) : this.servico.criarTopico(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success('Tópico salvo.');
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(t: TopicoChamado): Promise<void> {
    const ativar = !t.ativo;
    const ok = await this.confirmacao.confirm({
      title: ativar ? 'Ativar tópico' : 'Desativar tópico',
      message: ativar ? `O tópico ${t.nome} voltará a aparecer na abertura de chamados.` : `O tópico ${t.nome} deixará de aparecer em novas aberturas.`,
      confirmLabel: ativar ? 'Ativar' : 'Desativar',
      variant: ativar ? 'default' : 'destructive'
    });
    if (!ok) return;
    this.servico.alternarTopico(t.id, ativar).subscribe({
      next: () => {
        this.notificacoes.success(ativar ? 'Tópico ativado.' : 'Tópico desativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }
}
