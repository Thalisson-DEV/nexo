import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CesAutocompleteComponent,
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
import { Observable } from 'rxjs';

import { PerfilAcessoResponse, UsuarioImportacaoDTO, UsuarioRequest, UsuarioResponse, UsuarioUpdateRequest } from '../../../core/models/iam.models';
import { BaseOperacionalResponse, FuncaoResponse, ProcessoNegocioResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { LIMITE_EXPORTACAO, OPCOES_STATUS, dataIso, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';
import { formatarCpf } from '../../rh/colaboradores/colaboradores.component';
import { buscaColaboradores } from '../../rh/shared/vigencia';

type TipoUsuario = 'ADMINISTRATIVO' | 'CAMPO';
type Vinculo = 'NENHUM' | 'EXISTENTE' | 'NOVO';

const TIPOS: CesSelectOption<TipoUsuario>[] = [
  { label: 'Administrativo', value: 'ADMINISTRATIVO' },
  { label: 'Campo', value: 'CAMPO' }
];
const VINCULOS: CesSelectOption<Vinculo>[] = [
  { label: 'Sem colaborador', value: 'NENHUM' },
  { label: 'Colaborador já cadastrado', value: 'EXISTENTE' },
  { label: 'Cadastrar novo colaborador', value: 'NOVO' }
];
const OPCOES_VINCULO: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Com colaborador', value: true },
  { label: 'Sem colaborador', value: false }
];
const TEMAS: CesSelectOption<'light' | 'dark'>[] = [
  { label: 'Claro', value: 'light' },
  { label: 'Escuro', value: 'dark' }
];
const CORES: CesSelectOption<string>[] = [
  { label: 'Azul', value: 'primary' },
  { label: 'Esmeralda', value: 'emerald' },
  { label: 'Violeta', value: 'violet' },
  { label: 'Rosa', value: 'rose' },
  { label: 'Grafite', value: 'slate' }
];

@Component({
  selector: 'app-usuarios',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    CesAutocompleteComponent,
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
    CesImportDrawerComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesPasswordInputComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="novo()">
      <ces-page-header
        title="Usuários"
        icon="pi pi-id-card"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Cadastros' }, { label: 'Usuários' }]"
        description="Logins do sistema: perfil de acesso, tipo, vínculo com o colaborador e preferências."
        helpLabel="Ajuda sobre usuários"
        (help)="ajuda.show()"
      >
        <ces-button actions variant="outline" icon="pi pi-upload" label="Importar" (pressed)="importacao.aberta.set(true)"></ces-button>
        <ces-button actions icon="pi pi-plus" label="Novo usuário" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="iam-usuarios" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Vínculo" [options]="opcoesVinculo" [(value)]="lista.filters.vinculado"></ces-select>
          <ces-select label="Perfil" [options]="perfisOpcoes()" [(value)]="lista.filters.idPerfil" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
          <ces-select label="Tipo" [options]="tipos" [(value)]="lista.filters.tipo" [clearable]="true" placeholder="Todos"></ces-select>
          <ces-select label="Status do acesso" [options]="statusOpcoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Usuários"
        mode="server"
        storageKey="iam-usuarios"
        exportFileName="usuarios"
        searchPlaceholder="Nome, login ou e-mail"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Novo usuário"
        noResultsTitle="Nenhum usuário encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os usuários' : 'Nenhum usuário cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um usuário ou importe uma planilha.'"
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
        <ng-template cesCell="login" let-row><span class="ces-cell-strong">{{ row.login }}</span></ng-template>
        <ng-template cesCell="colaborador" let-row>
          @if (row.colaborador; as c) {
            <span>{{ c.nomeCompleto }}</span>
            <span class="ces-cell-muted">{{ c.baseOperacional || 'Sem base' }}</span>
            @if (!c.ativo) {
              <ces-status tone="warning" icon="pi-exclamation-triangle">Colaborador desligado</ces-status>
            }
          } @else {
            <span class="ces-cell-muted">Sem colaborador vinculado</span>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.login" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar usuário' : 'Novo usuário'" [description]="emEdicao()?.login"
      [dirty]="alterado()" size="lg">
      <ces-form-section title="Acesso" icon="pi pi-key">
        <div class="ces-form-grid">
          <ces-text-input label="Login" [required]="true" [(value)]="login" [maxlength]="9" placeholder="USR000000" [error]="erro('login')"></ces-text-input>
          <ces-select label="Tipo" [required]="true" [options]="tipos" [(value)]="tipo" [error]="erro('tipo')"></ces-select>
          <ces-select label="Perfil de acesso" [required]="true" [options]="perfisOpcoes()" [(value)]="idPerfil" [filter]="true" [error]="erro('perfil')"></ces-select>
          <ces-password-input [label]="emEdicao() ? 'Nova senha' : 'Senha'" [required]="!emEdicao()" [showOptional]="!!emEdicao()" [(value)]="senha"
            [description]="emEdicao() ? 'Em branco mantém a senha atual.' : undefined" [error]="erro('senha')"></ces-password-input>
          <ces-text-input label="E-mail" [(value)]="email" [maxlength]="150" placeholder="nome@empresa.com.br" [showOptional]="true" [error]="erro('email')"></ces-text-input>
          <ces-text-input label="Telefone" [(value)]="telefone" [maxlength]="20" placeholder="(00) 00000-0000" [showOptional]="true"></ces-text-input>
        </div>
        @if (emEdicao()) {
          <ces-boolean-control label="Acesso ativo" kind="toggle" [(value)]="ativo"></ces-boolean-control>
        }
      </ces-form-section>

      <ces-form-section title="Colaborador" icon="pi pi-user">
        @if (emEdicao(); as u) {
          @if (u.colaborador; as c) {
            <ces-callout [tone]="c.ativo ? 'info' : 'warning'" [title]="c.nomeCompleto">
              {{ c.funcao || 'Sem função' }} · {{ c.baseOperacional || 'sem base' }}{{ c.ativo ? '' : ' · colaborador desligado' }}
            </ces-callout>
          } @else {
            <ces-callout tone="neutral">Este acesso não está vinculado a um colaborador. O vínculo é feito pela tela de colaboradores.</ces-callout>
          }
        } @else {
          <ces-select label="Vínculo" [options]="vinculos" [(value)]="vinculo"></ces-select>
          @if (vinculo() === 'EXISTENTE') {
            <ces-autocomplete label="Colaborador" [required]="true" placeholder="Digite o nome" [options]="colaboradores.items()"
              [ngModel]="idColaborador()" (ngModelChange)="idColaborador.set($event)" [loading]="colaboradores.loading()"
              [status]="colaboradores.status()" [clearable]="true" [error]="erro('colaborador')"
              (search)="colaboradores.search($event)" (loadMore)="colaboradores.loadNext()" (retry)="colaboradores.retry()"></ces-autocomplete>
          } @else if (vinculo() === 'NOVO') {
            <div class="ces-form-grid">
              <ces-text-input label="Primeiro nome" [required]="true" [(value)]="primeiroNome" [error]="erro('primeiroNome')"></ces-text-input>
              <ces-text-input label="Último nome" [required]="true" [(value)]="ultimoNome" [error]="erro('ultimoNome')"></ces-text-input>
              <ces-text-input label="CPF" [required]="true" [value]="cpf()" (valueChange)="cpf.set(formatarCpf($event))" [maxlength]="14" placeholder="000.000.000-00" [error]="erro('cpf')"></ces-text-input>
              <ces-datepicker label="Data de contratação" [required]="true" [(value)]="dataContratacao" [error]="erro('dataContratacao')"></ces-datepicker>
              <ces-select label="Base operacional" [required]="true" [options]="basesOpcoes()" [(value)]="idBase" [filter]="true" [error]="erro('base')"></ces-select>
              <ces-select label="Processo" [required]="true" [options]="processosOpcoes()" [(value)]="idProcesso" [filter]="true" [error]="erro('processo')"></ces-select>
              <ces-select label="Função" [required]="true" [options]="funcoesOpcoes()" [(value)]="idFuncao" [filter]="true" [error]="erro('funcao')"></ces-select>
            </div>
          }
        }
      </ces-form-section>

      <ces-form-section title="Preferências" icon="pi pi-palette">
        <div class="ces-form-grid">
          <ces-select label="Tema" [options]="temas" [(value)]="tema"></ces-select>
          <ces-select label="Cor principal" [options]="cores" [(value)]="cor"></ces-select>
        </div>
        <ces-boolean-control label="Efeitos visuais ao clicar" kind="toggle" [(value)]="animacoes"></ces-boolean-control>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar usuário'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-import-drawer [(visible)]="importacao.aberta" title="Importar usuários" [blockOnErrors]="true"
      description="Planilha CSV com login, e-mail, telefone, senha, perfil, tipo e colaborador. A planilha só é processada sem linhas com erro."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()" (closed)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunasImportacao"
          [rows]="importacao.linhas()" [rowKey]="chaveImportacao" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="status" let-row>
            <ces-status [tone]="situacao(row.status).tone" [icon]="situacao(row.status).icon">{{ situacao(row.status).label }}</ces-status>
            @if (row.mensagemErro) {
              <span class="ces-cell-muted">{{ row.mensagemErro }}</span>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="importacao.remover(row)"></ces-icon-button>
          </ng-template>
        </ces-data-table>
      }
    </ces-import-drawer>

    <app-ajuda-listagem #ajuda titulo="Usuários" entidadePlural="usuários" busca="nome do colaborador, login ou e-mail" rotuloNovo="Novo usuário" [importacao]="true">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-user"></i> Vínculo com colaborador</h4>
        <p class="help-text-desc">No cadastro, o acesso pode ficar sem colaborador, ser ligado a um colaborador existente ou criar o colaborador junto. "Colaborador desligado" indica que a pessoa saiu, mesmo com o acesso ativo.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-lock"></i> Importação</h4>
        <p class="help-text-desc">A planilha traz senhas: ela só é processada sem linhas com erro, e as linhas são descartadas ao concluir ou fechar a importação.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class UsuariosComponent {
  private readonly usuarios = inject(UsuariosService);
  private readonly rh = inject(RhService);
  private readonly importacaoService = inject(ImportacaoService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  protected readonly formatarCpf = formatarCpf;
  protected readonly statusOpcoes = OPCOES_STATUS;
  protected readonly opcoesVinculo = OPCOES_VINCULO;
  protected readonly tipos = TIPOS;
  protected readonly vinculos = VINCULOS;
  protected readonly temas = TEMAS;
  protected readonly cores = CORES;

  protected readonly perfis = signal<PerfilAcessoResponse[]>([]);
  protected readonly bases = signal<BaseOperacionalResponse[]>([]);
  protected readonly processos = signal<ProcessoNegocioResponse[]>([]);
  protected readonly funcoes = signal<FuncaoResponse[]>([]);
  protected readonly perfisOpcoes = computed(() => this.perfis().map((p) => ({ label: p.nome, value: p.id })));
  protected readonly basesOpcoes = computed(() => this.bases().map((b) => ({ label: b.nome, value: b.id })));
  protected readonly processosOpcoes = computed(() => this.processos().map((p) => ({ label: p.nome, value: p.id })));
  protected readonly funcoesOpcoes = computed(() => this.funcoes().map((f) => ({ label: f.nome, value: f.id })));

  protected readonly lista = cesListState({
    storageKey: 'iam-usuarios',
    filters: {
      vinculado: cesFilter.value<boolean | null>(null, { label: 'Vínculo', param: 'vinculo', options: () => OPCOES_VINCULO }),
      idPerfil: cesFilter.value<string | null>(null, {
        label: 'Perfil',
        param: 'perfil',
        format: (id): string | null => (id ? (this.perfis().find((p) => p.id === id)?.nome ?? 'perfil selecionado') : null)
      }),
      tipo: cesFilter.value<TipoUsuario | null>(null, { label: 'Tipo', options: () => TIPOS }),
      ativo: cesFilter.value<boolean | null>(null, { label: 'Status', param: 'status', options: () => OPCOES_STATUS })
    },
    sort: [{ key: 'login', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<UsuarioResponse>(
    (pedido) => this.usuarios.listarUsuarios({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.usuarios.listarUsuarios({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<UsuarioResponse>[] = [
    { key: 'login', header: 'Login', type: 'custom', priority: 1, sortField: 'login', value: (u) => u.login, display: (u) => u.login },
    { key: 'colaborador', header: 'Colaborador', type: 'custom', priority: 1, value: (u) => u.colaborador?.nomeCompleto, display: (u) => u.colaborador?.nomeCompleto ?? 'Sem colaborador' },
    { key: 'email', header: 'E-mail', sortField: 'email', display: (u) => u.email || '—' },
    { key: 'tipo', header: 'Tipo', sortField: 'tipo', display: (u) => TIPOS.find((t) => t.value === u.tipo)?.label ?? '—' },
    { key: 'perfil', header: 'Perfil', sortField: 'perfil.nome', value: (u) => u.perfil?.nome, display: (u) => u.perfil?.nome ?? '—' },
    { key: 'ativo', header: 'Status', type: 'custom', sortField: 'ativo', value: (u) => u.ativo, display: (u) => (u.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly rotulo = (u: UsuarioResponse) => u.login;

  // Formulário
  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<UsuarioResponse | null>(null);
  protected readonly login = signal('');
  protected readonly email = signal('');
  protected readonly telefone = signal('');
  protected readonly tipo = signal<TipoUsuario | null>(null);
  protected readonly idPerfil = signal<string | null>(null);
  protected readonly senha = signal('');
  protected readonly ativo = signal(true);
  protected readonly tema = signal<'light' | 'dark'>('light');
  protected readonly cor = signal('primary');
  protected readonly animacoes = signal(true);
  protected readonly vinculo = signal<Vinculo>('NENHUM');
  protected readonly idColaborador = signal<string | null>(null);
  protected readonly primeiroNome = signal('');
  protected readonly ultimoNome = signal('');
  protected readonly cpf = signal('');
  protected readonly dataContratacao = signal<Date | null>(null);
  protected readonly idBase = signal<string | null>(null);
  protected readonly idProcesso = signal<string | null>(null);
  protected readonly idFuncao = signal<string | null>(null);
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  private readonly fotografia = signal('');
  protected readonly colaboradores = buscaColaboradores(() => this.idColaborador());

  protected readonly alterado = computed(() => this.formularioAberto() && this.estadoFormulario() !== this.fotografia());
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.login().trim()) e['login'] = 'Informe o login.';
    if (!this.tipo()) e['tipo'] = 'Escolha o tipo.';
    if (!this.idPerfil()) e['perfil'] = 'Escolha o perfil de acesso.';
    if (!this.emEdicao() && !this.senha()) e['senha'] = 'Informe a senha.';
    if (this.email().trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email().trim())) e['email'] = 'E-mail inválido.';
    if (!this.emEdicao() && this.vinculo() === 'EXISTENTE' && !this.idColaborador()) e['colaborador'] = 'Escolha o colaborador.';
    if (!this.emEdicao() && this.vinculo() === 'NOVO') {
      if (!this.primeiroNome().trim()) e['primeiroNome'] = 'Informe o primeiro nome.';
      if (!this.ultimoNome().trim()) e['ultimoNome'] = 'Informe o último nome.';
      if (this.cpf().replace(/\D/g, '').length !== 11) e['cpf'] = 'Informe os 11 dígitos do CPF.';
      if (!this.dataContratacao()) e['dataContratacao'] = 'Informe a data de contratação.';
      if (!this.idBase()) e['base'] = 'Escolha a base.';
      if (!this.idProcesso()) e['processo'] = 'Escolha o processo.';
      if (!this.idFuncao()) e['funcao'] = 'Escolha a função.';
    }
    return e;
  });

  protected readonly importacao = importacaoListagem<UsuarioImportacaoDTO>({
    enviar: (arquivo) => this.usuarios.validarImportacao(arquivo),
    confirmar: (usuarios) => this.usuarios.confirmarImportacao({ usuarios }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/iam/usuarios/importar/modelo', 'modelo-importacao-usuarios.csv'),
    aoProcessar: () => this.lista.reload(),
    sensivel: true
  });
  protected readonly situacao = cesImportStatus;
  protected readonly chaveImportacao = CHAVE_LINHA_IMPORTACAO;
  protected readonly colunasImportacao: CesDataTableColumn<UsuarioImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'login', header: 'Login', priority: 1 },
    { key: 'email', header: 'E-mail', display: (r) => r.email || '—' },
    { key: 'telefone', header: 'Telefone', display: (r) => r.telefone || '—' },
    { key: 'senha', header: 'Senha', value: () => null, display: (r) => (r.senha ? '••••••••' : '—') },
    { key: 'perfil', header: 'Perfil', value: (r) => r.nomePerfil, display: (r) => r.nomePerfil || '—' },
    { key: 'tipo', header: 'Tipo', display: (r) => r.tipo || '—' },
    { key: 'colaborador', header: 'Colaborador', value: (r) => r.nomeColaborador, display: (r) => r.nomeColaborador || '—' },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  constructor() {
    const ignorar = () => undefined;
    this.usuarios.listarPerfisAtivos().subscribe({ next: (r) => this.perfis.set(Array.isArray(r) ? r : []), error: ignorar });
    this.rh.listarBasesOperacionaisAtivas().subscribe({ next: (r) => this.bases.set(r.content ?? []), error: ignorar });
    this.rh.listarProcessosAtivos().subscribe({ next: (r) => this.processos.set(r.content ?? []), error: ignorar });
    this.rh.listarFuncoesAtivas().subscribe({ next: (r) => this.funcoes.set(r.content ?? []), error: ignorar });
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      vinculadoAColaborador: filters.vinculado ?? undefined,
      idPerfil: filters.idPerfil ?? undefined,
      tipo: filters.tipo ?? undefined,
      ativo: filters.ativo,
      buscaGlobal: query || undefined
    };
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(u: UsuarioResponse): void {
    this.preencher(u);
    this.formularioAberto.set(true);
  }

  private preencher(u: UsuarioResponse | null): void {
    this.emEdicao.set(u);
    this.login.set(u?.login ?? '');
    this.email.set(u?.email ?? '');
    this.telefone.set(u?.telefone ?? '');
    this.tipo.set(u?.tipo ?? null);
    this.idPerfil.set(u?.perfil?.id ?? null);
    this.senha.set('');
    this.ativo.set(u?.ativo ?? true);
    this.tema.set(u?.tema ?? 'light');
    this.cor.set(u?.corPrimaria || 'primary');
    this.animacoes.set(u?.animacoesAtivadas ?? true);
    this.vinculo.set('NENHUM');
    this.idColaborador.set(null);
    this.primeiroNome.set('');
    this.ultimoNome.set('');
    this.cpf.set('');
    this.dataContratacao.set(null);
    this.idBase.set(null);
    this.idProcesso.set(null);
    this.idFuncao.set(null);
    this.tentouSalvar.set(false);
    this.fotografia.set(this.estadoFormulario());
  }

  private estadoFormulario(): string {
    return JSON.stringify([
      this.login(),
      this.email(),
      this.telefone(),
      this.tipo(),
      this.idPerfil(),
      this.senha(),
      this.ativo(),
      this.tema(),
      this.cor(),
      this.animacoes(),
      this.vinculo(),
      this.idColaborador(),
      this.primeiroNome(),
      this.ultimoNome(),
      this.cpf(),
      this.dataContratacao()?.getTime() ?? null,
      this.idBase(),
      this.idProcesso(),
      this.idFuncao()
    ]);
  }

  private dadosUsuario(): UsuarioRequest {
    return {
      login: this.login().trim(),
      email: this.email().trim() || undefined,
      telefone: this.telefone().trim() || undefined,
      tipo: this.tipo()!,
      idPerfil: this.idPerfil()!,
      senha: this.senha() || undefined,
      tema: this.tema(),
      corPrimaria: this.cor(),
      animacoesAtivadas: this.animacoes()
    };
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    this.salvando.set(true);
    if (atual) {
      const ativo = this.ativo();
      const { senha, ...dados } = this.dadosUsuario();
      const request: UsuarioUpdateRequest = senha ? { ...dados, senha } : dados;
      this.usuarios.atualizarUsuario(atual.id, request).subscribe({
        next: () => {
          if (ativo === atual.ativo) return this.concluir('Usuário atualizado.');
          (ativo ? this.usuarios.ativarUsuario(atual.id) : this.usuarios.desativarUsuario(atual.id)).subscribe({
            next: () => this.concluir('Usuário atualizado.'),
            error: (e) => this.falhar(e)
          });
        },
        error: (e) => this.falhar(e)
      });
      return;
    }
    const usuario = this.dadosUsuario();
    if (this.vinculo() === 'EXISTENTE') {
      this.vincular(this.idColaborador()!, usuario);
    } else if (this.vinculo() === 'NOVO') {
      this.rh
        .criarColaborador({
          primeiroNome: this.primeiroNome().trim(),
          ultimoNome: this.ultimoNome().trim(),
          nomeCompleto: `${this.primeiroNome().trim()} ${this.ultimoNome().trim()}`,
          cpf: this.cpf().replace(/\D/g, ''),
          dataContratacao: dataIso(this.dataContratacao())!,
          idBaseOperacional: this.idBase()!,
          idProcesso: this.idProcesso()!,
          idFuncao: this.idFuncao()!
        })
        .subscribe({
          next: (colaborador) => {
            if (colaborador) return this.vincular(colaborador.id, usuario);
            // Sem conexão: o colaborador entra na fila; o acesso precisa dele no servidor.
            this.concluir(null);
            this.notificacoes.warning('Sem conexão: o colaborador será enviado quando a internet voltar. Gere o acesso depois, pela tela de colaboradores.');
          },
          error: (e) => this.falhar(e)
        });
    } else {
      this.executar(this.usuarios.criarUsuario(usuario), 'Usuário cadastrado.');
    }
  }

  private vincular(colaboradorId: string, usuario: UsuarioRequest): void {
    this.executar(this.rh.gerarAcesso({ colaboradorId, usuario }), 'Usuário cadastrado e vinculado ao colaborador.');
  }

  private executar(requisicao: Observable<unknown>, mensagem: string): void {
    requisicao.subscribe({ next: () => this.concluir(mensagem), error: (e) => this.falhar(e) });
  }

  private concluir(mensagem: string | null): void {
    this.salvando.set(false);
    this.formularioAberto.set(false);
    this.senha.set('');
    if (mensagem) this.notificacoes.success(mensagem);
    this.lista.reload();
  }

  private falhar(e: unknown): void {
    this.salvando.set(false);
    this.notificacoes.error(getErrorMessage(e));
  }

  protected async alternar(u: UsuarioResponse): Promise<void> {
    const desativar = u.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar acesso' : 'Ativar acesso',
      message: desativar ? `${u.login} deixa de conseguir entrar no sistema.` : `${u.login} volta a conseguir entrar no sistema.`,
      confirmLabel: desativar ? 'Desativar' : 'Ativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.usuarios.desativarUsuario(u.id) : this.usuarios.ativarUsuario(u.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? 'Acesso desativado.' : 'Acesso ativado.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }
}
