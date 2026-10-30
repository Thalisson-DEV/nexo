import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesPasswordInputComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService,
  cesFilter,
  cesImportStatus,
  cesListState
} from '@ces/ui';
import { Observable, of, switchMap } from 'rxjs';

import {
  BaseOperacionalResponse,
  ColaboradorImportacaoDTO,
  ColaboradorRequest,
  ColaboradorResponse,
  FuncaoResponse,
  PerfilAcessoResponse,
  ProcessoNegocioResponse
} from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { AuditDialogComponent } from '../../../shared/components/audit-dialog/audit-dialog.component';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, dataIso, dataLocal, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

type TipoUsuario = 'ADMINISTRATIVO' | 'CAMPO';

const LOGIN_VALIDO = /^[uU][sS][rR]\d{6}$/;

/** `00000000000` → `000.000.000-00`, aceitando digitação parcial. */
export function formatarCpf(valor: string | null | undefined): string {
  const d = (valor ?? '').replace(/\D/g, '').substring(0, 11);
  if (d.length > 9) return d.replace(/(\d{3})(\d{3})(\d{3})(\d{1,2})/, '$1.$2.$3-$4');
  if (d.length > 6) return d.replace(/(\d{3})(\d{3})(\d{1,3})/, '$1.$2.$3');
  if (d.length > 3) return d.replace(/(\d{3})(\d{1,3})/, '$1.$2');
  return d;
}

@Component({
  selector: 'app-colaboradores',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesBooleanControlComponent,
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesPasswordInputComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AuditDialogComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Colaboradores"
        icon="pi pi-users"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Colaboradores' }]"
        description="Quadro de pessoal com base, processo e função. O acesso ao sistema pode ser gerado no próprio cadastro."
        helpLabel="Ajuda sobre colaboradores"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo colaborador" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="rh-colaboradores" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-text-input label="CPF" [value]="lista.filters.cpf() ?? ''" (valueChange)="lista.filters.cpf.set(cpfOuNulo($event))"
            [maxlength]="14" placeholder="000.000.000-00" description="Busca exata. Não fica salvo no endereço da página."></ces-text-input>
          <ces-select label="Base operacional" [options]="basesOpcoes()" [(value)]="lista.filters.idBaseOperacional" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Processo" [options]="processosOpcoes()" [(value)]="lista.filters.idProcesso" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Função" [options]="funcoesOpcoes()" [(value)]="lista.filters.idFuncao" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
          <ces-select label="Status" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
          <ces-datepicker label="Contratados a partir de" [value]="dataLocal(lista.filters.contratacao().from)" [clearable]="true"
            (valueChange)="contratacao('from', $event)"></ces-datepicker>
          <ces-datepicker label="Contratados até" [value]="dataLocal(lista.filters.contratacao().to)" [clearable]="true"
            (valueChange)="contratacao('to', $event)"></ces-datepicker>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Colaboradores"
        mode="server"
        storageKey="rh-colaboradores"
        exportFileName="colaboradores"
        searchPlaceholder="Nome do colaborador"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo colaborador"
        noResultsTitle="Nenhum colaborador encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os colaboradores' : 'Nenhum colaborador cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um colaborador ou importe uma planilha.'"
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
        (emptyAction)="novo()"
        (rowActivate)="editar($event)"
      >
        <ng-template cesCell="nome" let-row>
          <span class="ces-cell-strong">{{ row.nomeCompleto }}</span>
          @if (row.idUsuario) {
            <span class="ces-cell-muted"><i class="pi pi-key" aria-hidden="true"></i> Com acesso ao sistema</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nomeCompleto" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            <ces-icon-button icon="pi pi-history" [label]="'Histórico de alterações de ' + row.nomeCompleto" size="icon-sm" (pressed)="logs(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar colaborador' : 'Novo colaborador'" [description]="emEdicao()?.nomeCompleto"
      [dirty]="alterado()" size="lg">
      <ces-form-section title="Dados pessoais" icon="pi pi-user">
        <div class="ces-form-grid">
          <ces-text-input label="Primeiro nome" [required]="true" [(value)]="primeiroNome" [maxlength]="100" [error]="erro('primeiroNome')"></ces-text-input>
          <ces-text-input label="Último nome" [required]="true" [(value)]="ultimoNome" [maxlength]="100" [error]="erro('ultimoNome')"></ces-text-input>
        </div>
        <ces-text-input label="Nome completo" [required]="true" [(value)]="nomeCompleto" [maxlength]="200" [error]="erro('nomeCompleto')"></ces-text-input>
        <div class="ces-form-grid">
          <ces-text-input label="CPF" [required]="true" [value]="cpf()" (valueChange)="cpf.set(formatarCpf($event))" [maxlength]="14"
            placeholder="000.000.000-00" [readonly]="!!emEdicao()" [description]="emEdicao() ? 'O CPF não pode ser alterado.' : undefined"
            [error]="erro('cpf')"></ces-text-input>
          <ces-datepicker label="Data de contratação" [required]="true" [(value)]="dataContratacao" [readonly]="!!emEdicao()" [error]="erro('dataContratacao')"></ces-datepicker>
        </div>
        @if (emEdicao()) {
          <ces-boolean-control label="Colaborador ativo" kind="toggle" [(value)]="ativo"></ces-boolean-control>
        }
      </ces-form-section>

      <ces-form-section title="Vínculo" icon="pi pi-sitemap">
        <div class="ces-form-grid">
          <ces-select label="Base operacional" [required]="true" [options]="basesOpcoes()" [(value)]="idBase" [filter]="true" [error]="erro('base')"></ces-select>
          <ces-select label="Processo" [required]="true" [options]="processosOpcoes()" [(value)]="idProcesso" [filter]="true" [error]="erro('processo')"></ces-select>
          <ces-select label="Função" [required]="true" [options]="funcoesOpcoes()" [(value)]="idFuncao" [filter]="true" [error]="erro('funcao')"></ces-select>
        </div>
      </ces-form-section>

      @if (!emEdicao()?.idUsuario) {
        <ces-form-section title="Acesso ao sistema" icon="pi pi-key" description="Opcional. Cria o usuário de login junto com o cadastro.">
          <ces-boolean-control label="Gerar acesso ao sistema" kind="toggle" [(value)]="gerarAcesso"></ces-boolean-control>
          @if (gerarAcesso()) {
            <div class="ces-form-grid">
              <ces-text-input label="Login" [required]="true" [value]="login()" (valueChange)="login.set($event.toUpperCase())" [maxlength]="9"
                placeholder="USR000000" description="USR seguido de 6 dígitos." [error]="erro('login')"></ces-text-input>
              <ces-password-input label="Senha inicial" [required]="true" [(value)]="senha" [minlength]="6" [error]="erro('senha')"></ces-password-input>
              <ces-select label="Perfil de acesso" [required]="true" [options]="perfisOpcoes()" [(value)]="idPerfil" [filter]="true" [error]="erro('perfil')"></ces-select>
              <ces-select label="Tipo de usuário" [required]="true" [options]="tiposUsuario" [(value)]="tipo"></ces-select>
            </div>
          }
        </ces-form-section>
      }
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar colaborador'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar colaboradores"
      description="Planilha CSV com nome, CPF, data de contratação, base, processo e função (pelos nomes cadastrados)."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="nome" let-row>
            @if (row.editing) { <ces-text-input label="Nome completo" [labelVisible]="false" [(value)]="row.nomeCompleto"></ces-text-input> } @else { {{ row.nomeCompleto }} }
          </ng-template>
          <ng-template cesCell="cpf" let-row>
            @if (row.editing) {
              <ces-text-input label="CPF" [labelVisible]="false" [value]="formatarCpf(row.cpf)" (valueChange)="row.cpf = formatarCpf($event)" [maxlength]="14"></ces-text-input>
            } @else {
              {{ formatarCpf(row.cpf) }}
            }
          </ng-template>
          <ng-template cesCell="contratacao" let-row>
            @if (row.editing) {
              <ces-datepicker label="Data de contratação" [labelVisible]="false" [value]="dataLocal(row.dataContratacao)" (valueChange)="row.dataContratacao = dataIso($event) ?? ''"></ces-datepicker>
            } @else {
              {{ dataBr(row.dataContratacao) }}
            }
          </ng-template>
          <ng-template cesCell="base" let-row>
            @if (row.editing) {
              <ces-select label="Base" [labelVisible]="false" [options]="basesPorNome()" [(value)]="row.nomeBase" [filter]="true"></ces-select>
            } @else {
              {{ row.nomeBase || '—' }}
            }
          </ng-template>
          <ng-template cesCell="processo" let-row>
            @if (row.editing) {
              <ces-select label="Processo" [labelVisible]="false" [options]="processosPorNome()" [(value)]="row.nomeProcesso" [filter]="true"></ces-select>
            } @else {
              {{ row.nomeProcesso || '—' }}
            }
          </ng-template>
          <ng-template cesCell="funcao" let-row>
            @if (row.editing) {
              <ces-select label="Função" [labelVisible]="false" [options]="funcoesPorNome()" [(value)]="row.nomeFuncao" [filter]="true"></ces-select>
            } @else {
              {{ row.nomeFuncao || '—' }}
            }
          </ng-template>
          <ng-template cesCell="status" let-row>
            <ces-status [tone]="situacao(row.status).tone" [icon]="situacao(row.status).icon">{{ situacao(row.status).label }}</ces-status>
            @if (row.mensagemErro) {
              <span class="ces-cell-muted">{{ row.mensagemErro }}</span>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              @if (row.editing) {
                <ces-icon-button icon="pi pi-check" [label]="'Concluir correção da linha ' + row.linha" size="icon-sm" (pressed)="importacao.concluirCorrecao(row)"></ces-icon-button>
              } @else {
                <ces-icon-button icon="pi pi-pencil" [label]="'Corrigir linha ' + row.linha" size="icon-sm" (pressed)="row.editing = true"></ces-icon-button>
                <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="importacao.remover(row)"></ces-icon-button>
              }
            </span>
          </ng-template>
        </ces-data-table>
      }
    </ces-import-drawer>

    <app-audit-dialog #auditoria></app-audit-dialog>
    <app-ajuda-listagem #ajuda titulo="Colaboradores" entidadePlural="colaboradores" busca="nome do colaborador" rotuloNovo="Novo colaborador"
      [importacao]="true" video="https://www.youtube.com/embed/ZSV3YnVRH2o">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-key"></i> Acesso ao sistema</h4>
        <p class="help-text-desc">Ao cadastrar (ou editar quem ainda não tem usuário), ligue "Gerar acesso ao sistema" e informe login (USR + 6 dígitos), senha inicial, perfil e tipo.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-id-card"></i> Busca por CPF</h4>
        <p class="help-text-desc">O filtro de CPF é exato e, por ser dado pessoal, não fica salvo no endereço da página nem no navegador.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class ColaboradoresComponent {
  private readonly rh = inject(RhService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly auditoria = viewChild.required<AuditDialogComponent>('auditoria');

  protected readonly formatarCpf = formatarCpf;
  protected readonly dataLocal = dataLocal;
  protected readonly dataIso = dataIso;
  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly tiposUsuario: CesSelectOption<TipoUsuario>[] = [
    { label: 'Campo', value: 'CAMPO' },
    { label: 'Administrativo', value: 'ADMINISTRATIVO' }
  ];

  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly funcoes = signal<FuncaoResponse[]>([]);
  protected readonly perfis = signal<PerfilAcessoResponse[]>([]);
  protected readonly basesOpcoes = computed(() => opcoes(this.bases(), 'id'));
  protected readonly processosOpcoes = computed(() => opcoes(this.processos(), 'id'));
  protected readonly funcoesOpcoes = computed(() => opcoes(this.funcoes(), 'id'));
  protected readonly perfisOpcoes = computed(() => opcoes(this.perfis(), 'id'));
  protected readonly basesPorNome = computed(() => opcoes(this.bases(), 'nome'));
  protected readonly processosPorNome = computed(() => opcoes(this.processos(), 'nome'));
  protected readonly funcoesPorNome = computed(() => opcoes(this.funcoes(), 'nome'));

  protected readonly lista = cesListState({
    storageKey: 'rh-colaboradores',
    filters: {
      // Dado pessoal (LGPD): não vai para a URL.
      cpf: cesFilter.value<string | null>(null, { label: 'CPF', url: false }),
      idBaseOperacional: cesFilter.value<string | null>(null, { label: 'Base', param: 'base', format: (id): string | null => (id ? this.nome(this.bases(), id) : null) }),
      idProcesso: cesFilter.value<string | null>(null, { label: 'Processo', param: 'processo', format: (id): string | null => (id ? this.nome(this.processos(), id) : null) }),
      idFuncao: cesFilter.value<string | null>(null, { label: 'Função', param: 'funcao', format: (id): string | null => (id ? this.nome(this.funcoes(), id) : null) }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS }),
      contratacao: cesFilter.range({ from: null, to: null }, { label: 'Contratação' })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<ColaboradorResponse>(
    (pedido) => this.rh.listarColaboradores({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.rh.listarColaboradores({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<ColaboradorResponse>[] = [
    { key: 'nome', header: 'Nome', type: 'custom', priority: 1, sortField: 'nomeCompleto', value: (c) => c.nomeCompleto, display: (c) => c.nomeCompleto },
    { key: 'cpf', header: 'CPF', sortField: 'cpf', value: (c) => c.cpf, display: (c) => formatarCpf(c.cpf) },
    { key: 'base', header: 'Base', sortField: 'baseOperacional.nome', value: (c) => c.baseOperacional?.nome, display: (c) => c.baseOperacional?.nome ?? '—' },
    { key: 'processo', header: 'Processo', sortField: 'processo.nome', value: (c) => c.processo?.nome, display: (c) => c.processo?.nome ?? '—' },
    { key: 'funcao', header: 'Função', priority: 1, sortField: 'funcao.nome', value: (c) => c.funcao?.nome, display: (c) => c.funcao?.nome ?? '—' },
    { key: 'contratacao', header: 'Contratação', type: 'date', sortField: 'dataContratacao', value: (c) => c.dataContratacao },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (c) => c.ativo, display: (c) => (c.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (c: ColaboradorResponse) => c.nomeCompleto;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<ColaboradorResponse | null>(null);
  protected readonly primeiroNome = signal('');
  protected readonly ultimoNome = signal('');
  protected readonly nomeCompleto = signal('');
  protected readonly cpf = signal('');
  protected readonly dataContratacao = signal<Date | null>(null);
  protected readonly idBase = signal<string | null>(null);
  protected readonly idProcesso = signal<string | null>(null);
  protected readonly idFuncao = signal<string | null>(null);
  protected readonly ativo = signal(true);
  protected readonly gerarAcesso = signal(false);
  protected readonly login = signal('');
  protected readonly senha = signal('');
  protected readonly idPerfil = signal<string | null>(null);
  protected readonly tipo = signal<TipoUsuario>('CAMPO');
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);

  protected readonly alterado = computed(() => {
    const c = this.emEdicao();
    const acesso = this.gerarAcesso() && !!(this.login() + this.senha()).trim();
    if (!c) {
      return acesso || !!(this.primeiroNome() + this.ultimoNome() + this.nomeCompleto() + this.cpf()).trim() || !!this.dataContratacao() || !!this.idBase() || !!this.idProcesso() || !!this.idFuncao();
    }
    return (
      acesso ||
      this.primeiroNome() !== c.primeiroNome ||
      this.ultimoNome() !== c.ultimoNome ||
      this.nomeCompleto() !== c.nomeCompleto ||
      this.idBase() !== (c.baseOperacional?.id ?? null) ||
      this.idProcesso() !== (c.processo?.id ?? null) ||
      this.idFuncao() !== (c.funcao?.id ?? null) ||
      this.ativo() !== c.ativo
    );
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.primeiroNome().trim()) e['primeiroNome'] = 'Informe o primeiro nome.';
    if (!this.ultimoNome().trim()) e['ultimoNome'] = 'Informe o último nome.';
    if (!this.nomeCompleto().trim()) e['nomeCompleto'] = 'Informe o nome completo.';
    if (this.cpf().replace(/\D/g, '').length !== 11) e['cpf'] = 'Informe os 11 dígitos do CPF.';
    if (!this.dataContratacao()) e['dataContratacao'] = 'Informe a data de contratação.';
    if (!this.idBase()) e['base'] = 'Escolha a base.';
    if (!this.idProcesso()) e['processo'] = 'Escolha o processo.';
    if (!this.idFuncao()) e['funcao'] = 'Escolha a função.';
    if (this.criaAcesso()) {
      if (!LOGIN_VALIDO.test(this.login())) e['login'] = 'Use USR seguido de 6 dígitos (ex.: USR123456).';
      if (this.senha().length < 6) e['senha'] = 'A senha precisa de pelo menos 6 caracteres.';
      if (!this.idPerfil()) e['perfil'] = 'Escolha o perfil de acesso.';
    }
    return e;
  });

  protected readonly importacao = importacaoListagem<ColaboradorImportacaoDTO>({
    enviar: (arquivo) => this.rh.importarColaboradores(arquivo),
    confirmar: (colaboradores) => this.rh.confirmarImportacao({ colaboradores }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/rh/colaboradores/importar/modelo', 'modelo-colaboradores.csv'),
    aoProcessar: () => this.lista.reload()
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<ColaboradorImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'nome', header: 'Nome', type: 'custom', priority: 1, value: (r) => r.nomeCompleto },
    { key: 'cpf', header: 'CPF', type: 'custom', value: (r) => r.cpf },
    { key: 'contratacao', header: 'Contratação', type: 'custom', value: (r) => r.dataContratacao },
    { key: 'base', header: 'Base', type: 'custom', value: (r) => r.nomeBase },
    { key: 'processo', header: 'Processo', type: 'custom', value: (r) => r.nomeProcesso },
    { key: 'funcao', header: 'Função', type: 'custom', value: (r) => r.nomeFuncao },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    this.rh.listarBasesOperacionaisAtivas().subscribe({ next: (r) => this.bases.set(r.content), error: () => undefined });
    this.rh.listarProcessosAtivos().subscribe({ next: (r) => this.processos.set(r.content), error: () => undefined });
    this.rh.listarFuncoesAtivas().subscribe({ next: (r) => this.funcoes.set(r.content), error: () => undefined });
    this.rh.listarPerfisAcessoAtivos().subscribe({ next: (r) => this.perfis.set(r), error: () => undefined });
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      nomeCompleto: query || undefined,
      cpf: filters.cpf?.replace(/\D/g, '') || undefined,
      idBaseOperacional: filters.idBaseOperacional,
      idProcesso: filters.idProcesso,
      idFuncao: filters.idFuncao,
      ativo: filters.ativo,
      dataContratacao: filters.contratacao.from ?? undefined,
      dataContratacaoFim: filters.contratacao.to ?? undefined
    };
  }

  private nome(itens: readonly { id: string; nome: string }[], id: string): string {
    return itens.find((i) => i.id === id)?.nome ?? 'selecionado';
  }

  protected cpfOuNulo(valor: string): string | null {
    return formatarCpf(valor) || null;
  }

  protected contratacao(limite: 'from' | 'to', data: Date | null): void {
    this.lista.filters.contratacao.update((atual) => ({ ...atual, [limite]: dataIso(data) }));
  }

  protected dataBr(iso: string | undefined): string {
    const d = dataLocal(iso);
    return d ? d.toLocaleDateString('pt-BR') : '—';
  }

  private criaAcesso(): boolean {
    return this.gerarAcesso() && !this.emEdicao()?.idUsuario;
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(c: ColaboradorResponse): void {
    this.preencher(c);
    this.formularioAberto.set(true);
  }

  private preencher(c: ColaboradorResponse | null): void {
    this.emEdicao.set(c);
    this.primeiroNome.set(c?.primeiroNome ?? '');
    this.ultimoNome.set(c?.ultimoNome ?? '');
    this.nomeCompleto.set(c?.nomeCompleto ?? '');
    this.cpf.set(formatarCpf(c?.cpf));
    this.dataContratacao.set(dataLocal(c?.dataContratacao));
    this.idBase.set(c?.baseOperacional?.id ?? null);
    this.idProcesso.set(c?.processo?.id ?? null);
    this.idFuncao.set(c?.funcao?.id ?? null);
    this.ativo.set(c?.ativo ?? true);
    this.gerarAcesso.set(false);
    this.login.set('');
    this.senha.set('');
    this.idPerfil.set(null);
    this.tipo.set('CAMPO');
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const ativo = this.ativo();
    const criaAcesso = this.criaAcesso();
    const request: ColaboradorRequest = {
      primeiroNome: this.primeiroNome().trim(),
      ultimoNome: this.ultimoNome().trim(),
      nomeCompleto: this.nomeCompleto().trim(),
      cpf: this.cpf().replace(/\D/g, ''),
      dataContratacao: dataIso(this.dataContratacao())!,
      idProcesso: this.idProcesso()!,
      idBaseOperacional: this.idBase()!,
      idFuncao: this.idFuncao()!
    };
    this.salvando.set(true);
    const salvar$: Observable<ColaboradorResponse | null> = atual
      ? this.rh.atualizarColaborador(atual.id, request).pipe(
          switchMap((r) =>
            r && r.ativo !== ativo
              ? (ativo ? this.rh.ativarColaborador(atual.id) : this.rh.desativarColaborador(atual.id)).pipe(switchMap(() => of(r)))
              : of(r)
          )
        )
      : this.rh.criarColaborador(request);
    salvar$.subscribe({
      next: (salvo) => {
        // `null` = gravação enfileirada offline; o acesso só pode ser gerado com o colaborador no servidor.
        if (criaAcesso && salvo) {
          this.rh
            .gerarAcesso({ colaboradorId: salvo.id, usuario: { login: this.login().toUpperCase(), senha: this.senha(), idPerfil: this.idPerfil()!, tipo: this.tipo() } })
            .subscribe({
              next: () => this.concluir(atual ? 'Colaborador atualizado e acesso gerado.' : 'Colaborador cadastrado e acesso gerado.'),
              error: (e) => {
                this.concluir(null);
                this.notificacoes.warning(`Colaborador salvo, mas o acesso não foi gerado: ${getErrorMessage(e)}`);
              }
            });
          return;
        }
        this.concluir(atual ? 'Colaborador atualizado.' : 'Colaborador cadastrado.');
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  private concluir(mensagem: string | null): void {
    this.salvando.set(false);
    this.formularioAberto.set(false);
    if (mensagem) this.notificacoes.success(mensagem);
    this.lista.reload();
  }

  protected async alternar(c: ColaboradorResponse): Promise<void> {
    const desativar = c.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar colaborador' : 'Ativar colaborador',
      message: desativar ? `${c.nomeCompleto} deixa de aparecer nas seleções. O histórico é mantido.` : `${c.nomeCompleto} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.rh.desativarColaborador(c.id) : this.rh.ativarColaborador(c.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Colaborador desativado.' : 'Colaborador ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected logs(c: ColaboradorResponse): void {
    this.auditoria().show(c.id, c.nomeCompleto, {
      primeiroNome: 'Primeiro nome',
      ultimoNome: 'Último nome',
      nomeCompleto: 'Nome completo',
      cpf: 'CPF',
      dataContratacao: 'Data de contratação',
      dataDesativacao: 'Data de desativação',
      ativo: 'Status (ativo)',
      baseOperacional: 'Base operacional',
      processo: 'Processo de negócio',
      funcao: 'Função',
      idUsuario: 'Vínculo de usuário'
    });
  }
}

function opcoes(itens: readonly { id: string; nome: string }[], valor: 'id' | 'nome'): CesSelectOption<string>[] {
  return itens.map((i) => ({ label: i.nome, value: i[valor] }));
}
