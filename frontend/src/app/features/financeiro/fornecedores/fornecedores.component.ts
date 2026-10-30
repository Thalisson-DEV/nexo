import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  CesTone,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { Observable } from 'rxjs';

import { Fornecedor, FornecedorRequest } from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';

type Curadoria = Fornecedor['status'];

const CURADORIA: CesSelectOption<Curadoria | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Pendente', value: 'PENDENTE' },
  { label: 'Homologado', value: 'HOMOLOGADO' },
  { label: 'Bloqueado', value: 'BLOQUEADO' }
];
const DISPONIBILIDADE: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Somente utilizáveis', value: true },
  { label: 'Somente indisponíveis', value: false }
];
const ATIVOS: CesSelectOption<boolean | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Ativos', value: true },
  { label: 'Inativos', value: false }
];
const TOM_CURADORIA: Record<Curadoria, CesTone> = { PENDENTE: 'warning', HOMOLOGADO: 'success', BLOQUEADO: 'danger' };

/** `00000000000` / `00000000000000` → CPF / CNPJ formatado. */
export function formatarDocumento(valor?: string | null): string {
  if (!valor) return '';
  const d = valor.replace(/\D/g, '');
  return d.length === 14
    ? d.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5')
    : d.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4');
}

@Component({
  selector: 'app-fornecedores',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeEditar" (newRequested)="novo()">
      <ces-page-header
        title="Fornecedores"
        icon="pi pi-building"
        [breadcrumb]="[{ label: 'Financeiro' }, { label: 'Admin' }, { label: 'Fornecedores' }]"
        description="Cadastro e curadoria (homologação e bloqueio) dos fornecedores usados nas solicitações financeiras."
        helpLabel="Ajuda sobre fornecedores"
        (help)="ajuda.show()"
      >
        @if (podeEditar) {
          <ces-button actions icon="pi pi-plus" label="Novo fornecedor" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel storageKey="fin-fornecedores" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Curadoria" [options]="curadoria" [(value)]="lista.filters.status"></ces-select>
          <ces-select label="Disponibilidade" [options]="disponibilidade" [(value)]="lista.filters.utilizavel"></ces-select>
          <ces-select label="Status" [options]="ativos" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Fornecedores"
        mode="server"
        storageKey="fin-fornecedores"
        exportFileName="fornecedores"
        searchPlaceholder="Nome, razão social ou CPF/CNPJ"
        [rowActionLabel]="podeEditar ? 'Editar' : undefined"
        rowActionIcon="pi pi-pencil"
        [emptyActionLabel]="podeEditar ? 'Novo fornecedor' : undefined"
        noResultsTitle="Nenhum fornecedor encontrado com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar os fornecedores' : 'Nenhum fornecedor cadastrado'"
        [stateDescription]="resultado.error() ?? 'Cadastre um fornecedor: só o nome fantasia é obrigatório.'"
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
        (rowActivate)="podeEditar && editar($event)"
      >
        <ng-template cesCell="nome" let-row>
          <span class="ces-cell-strong">{{ row.nomeFantasia }}</span>
          <span class="ces-cell-muted">{{ row.razaoSocial || 'Razão social não informada' }}</span>
        </ng-template>
        <ng-template cesCell="curadoria" let-row>
          <ces-status [tone]="tom(row.status)">{{ rotuloCuradoria(row.status) }}</ces-status>
          @if (row.motivoBloqueio) {
            <span class="ces-cell-muted">{{ row.motivoBloqueio }}</span>
          }
        </ng-template>
        <ng-template cesCell="utilizavel" let-row>
          <ces-status [tone]="row.utilizavel ? 'success' : 'neutral'" [emphasis]="row.utilizavel ? 'soft' : 'outline'" [icon]="row.utilizavel ? 'pi-check-circle' : 'pi-ban'">
            {{ row.utilizavel ? 'Sim' : 'Não' }}
          </ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            @if (podeHomologar && row.status === 'PENDENTE') {
              <ces-icon-button icon="pi pi-verified" [label]="'Homologar ' + row.nomeFantasia" size="icon-sm" (pressed)="homologar(row)"></ces-icon-button>
            }
            @if (podeHomologar && row.status !== 'BLOQUEADO') {
              <ces-icon-button icon="pi pi-lock" [label]="'Bloquear ' + row.nomeFantasia" size="icon-sm" (pressed)="abrirBloqueio(row)"></ces-icon-button>
            }
            @if (podeHomologar && row.status === 'BLOQUEADO') {
              <ces-icon-button icon="pi pi-lock-open" [label]="'Desbloquear ' + row.nomeFantasia" size="icon-sm" (pressed)="executar(row, 'desbloquear', 'Fornecedor desbloqueado.')"></ces-icon-button>
            }
            @if (podeEditar) {
              <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nomeFantasia" size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            }
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? 'Editar fornecedor' : 'Novo fornecedor'" [description]="emEdicao()?.nomeFantasia"
      [dirty]="alterado()" size="md">
      <ces-form-section title="Identificação" icon="pi pi-building">
        <ces-text-input label="Nome fantasia" [required]="true" [(value)]="nomeFantasia" [maxlength]="150" placeholder="Nome pelo qual o fornecedor é conhecido" [error]="erro('nome')"></ces-text-input>
        <ces-text-input label="Razão social" [(value)]="razaoSocial" [maxlength]="200" [showOptional]="true" description="Necessária para homologar."></ces-text-input>
        <ces-text-input label="CPF ou CNPJ" [(value)]="documento" [maxlength]="18" [showOptional]="true" placeholder="Somente números ou com máscara"
          description="Necessário para homologar; não pode repetir." [error]="erro('documento')"></ces-text-input>
      </ces-form-section>
      <ces-form-section title="Financeiro" icon="pi pi-wallet">
        <ces-textarea label="Dados bancários" [(value)]="dadosBancarios" [rows]="3" [showOptional]="true" placeholder="Banco, agência, conta, tipo e titular"
          description="Dado sensível: guardado cifrado e não pesquisável."></ces-textarea>
        <ces-textarea label="Observações" [(value)]="observacoes" [rows]="3" [showOptional]="true" placeholder="Informações úteis para a equipe financeira"></ces-textarea>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar fornecedor'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <ces-form-drawer [(visible)]="bloqueioAberto" title="Bloquear fornecedor" [description]="emBloqueio()?.nomeFantasia" [dirty]="!!motivo().trim()" size="sm">
      <ces-callout tone="warning">Novas solicitações não poderão usar este fornecedor até o desbloqueio.</ces-callout>
      <ces-textarea label="Motivo do bloqueio" [required]="true" [(value)]="motivo" [rows]="4" [error]="tentouBloquear() && !motivo().trim() ? 'Informe o motivo.' : undefined"></ces-textarea>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Voltar" (pressed)="bloqueioAberto.set(false)"></ces-button>
        <ces-button variant="destructive" icon="pi pi-lock" label="Confirmar bloqueio" [loading]="salvando()" (pressed)="bloquear()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda titulo="Fornecedores" entidadePlural="fornecedores" busca="nome, razão social ou CPF/CNPJ" rotuloNovo="Novo fornecedor" [podeCriar]="podeEditar">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-verified"></i> Curadoria</h4>
        <p class="help-text-desc">Cadastros rápidos (só o nome fantasia) nascem pendentes e já podem ser usados. Para homologar, complete razão social e CPF/CNPJ. Bloqueados deixam de aparecer em novas solicitações; o motivo é obrigatório.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-id-card"></i> Busca</h4>
        <p class="help-text-desc">A busca aceita CPF/CNPJ e, por isso, não fica salva no endereço da página.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class FornecedoresComponent {
  private readonly financeiro = inject(FinanceiroService);
  private readonly auth = inject(AuthService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  protected readonly podeEditar = this.temPermissao('financeiro:fornecedor:write');
  protected readonly podeHomologar = this.temPermissao('financeiro:fornecedor:homologar');
  protected readonly curadoria = CURADORIA;
  protected readonly disponibilidade = DISPONIBILIDADE;
  protected readonly ativos = ATIVOS;
  protected readonly formatarDocumento = formatarDocumento;

  protected readonly lista = cesListState({
    storageKey: 'fin-fornecedores',
    // A busca pode ser o CPF de um fornecedor pessoa física: não vai para a URL.
    queryParam: false,
    filters: {
      status: cesFilter.value<Curadoria | null>(null, { label: 'Curadoria', type: 'string', options: () => CURADORIA }),
      utilizavel: cesFilter.value<boolean | null>(null, { label: 'Disponibilidade', options: () => DISPONIBILIDADE }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', options: () => ATIVOS })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<Fornecedor>(
    (pedido) => this.financeiro.listarFornecedores({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.financeiro.listarFornecedores({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<Fornecedor>[] = [
    { key: 'nome', header: 'Fornecedor', type: 'custom', priority: 1, sortField: 'nomeFantasia', value: (f) => f.nomeFantasia, display: (f) => f.nomeFantasia },
    { key: 'documento', header: 'CPF / CNPJ', value: (f) => f.documento, display: (f) => formatarDocumento(f.documento) || '—' },
    { key: 'curadoria', header: 'Curadoria', type: 'custom', priority: 1, sortField: 'status', value: (f) => f.status, display: (f) => this.rotuloCuradoria(f.status) },
    { key: 'utilizavel', header: 'Disponível', type: 'custom', value: (f) => f.utilizavel, display: (f) => (f.utilizavel ? 'Sim' : 'Não') }
  ];
  protected readonly rotulo = (f: Fornecedor) => f.nomeFantasia;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<Fornecedor | null>(null);
  protected readonly nomeFantasia = signal('');
  protected readonly razaoSocial = signal('');
  protected readonly documento = signal('');
  protected readonly dadosBancarios = signal('');
  protected readonly observacoes = signal('');
  protected readonly salvando = signal(false);
  private readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const f = this.emEdicao();
    const atual = [this.nomeFantasia(), this.razaoSocial(), this.documento(), this.dadosBancarios(), this.observacoes()];
    const original = [f?.nomeFantasia ?? '', f?.razaoSocial ?? '', f?.documento ?? '', f?.dadosBancarios ?? '', f?.observacoes ?? ''];
    return atual.some((v, i) => v !== original[i]);
  });
  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.nomeFantasia().trim()) e['nome'] = 'Informe o nome fantasia.';
    const digitos = this.documento().replace(/\D/g, '');
    if (digitos && digitos.length !== 11 && digitos.length !== 14) e['documento'] = 'Informe um CPF com 11 ou um CNPJ com 14 dígitos.';
    return e;
  });

  protected readonly bloqueioAberto = signal(false);
  protected readonly emBloqueio = signal<Fornecedor | null>(null);
  protected readonly motivo = signal('');
  protected readonly tentouBloquear = signal(false);

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      buscaGlobal: query || undefined,
      status: filters.status ?? undefined,
      apenasUtilizaveis: filters.utilizavel ?? undefined,
      ativo: filters.ativo ?? undefined
    };
  }

  protected tom(status: Curadoria): CesTone {
    return TOM_CURADORIA[status] ?? 'neutral';
  }

  protected rotuloCuradoria(status: Curadoria): string {
    return CURADORIA.find((c) => c.value === status)?.label ?? status;
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected novo(): void {
    if (!this.podeEditar) return;
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(f: Fornecedor): void {
    this.preencher(f);
    this.formularioAberto.set(true);
  }

  private preencher(f: Fornecedor | null): void {
    this.emEdicao.set(f);
    this.nomeFantasia.set(f?.nomeFantasia ?? '');
    this.razaoSocial.set(f?.razaoSocial ?? '');
    this.documento.set(f?.documento ?? '');
    this.dadosBancarios.set(f?.dadosBancarios ?? '');
    this.observacoes.set(f?.observacoes ?? '');
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) return;
    const atual = this.emEdicao();
    const request: FornecedorRequest = {
      nomeFantasia: this.nomeFantasia().trim(),
      razaoSocial: this.razaoSocial().trim() || null,
      documento: this.documento().trim() || null,
      dadosBancarios: this.dadosBancarios().trim() || null,
      observacoes: this.observacoes().trim() || null
    };
    this.salvando.set(true);
    const salvar$: Observable<unknown> = atual ? this.financeiro.atualizarFornecedor(atual.id, request) : this.financeiro.criarFornecedor(request);
    salvar$.subscribe({
      next: () => {
        this.formularioAberto.set(false);
        this.concluir(atual ? 'Fornecedor atualizado.' : 'Fornecedor cadastrado.');
      },
      error: (e) => this.falhar(e)
    });
  }

  protected homologar(f: Fornecedor): void {
    if (!f.razaoSocial || !f.documento) {
      this.notificacoes.warning('Informe razão social e CPF/CNPJ antes de homologar.');
      if (this.podeEditar) this.editar(f);
      return;
    }
    this.executar(f, 'homologar', 'Fornecedor homologado.');
  }

  protected executar(f: Fornecedor, acao: 'homologar' | 'desbloquear' | 'ativar', mensagem: string): void {
    this.financeiro.acaoFornecedor(f.id, acao).subscribe({ next: () => this.concluir(mensagem), error: (e) => this.falhar(e) });
  }

  protected abrirBloqueio(f: Fornecedor): void {
    this.emBloqueio.set(f);
    this.motivo.set('');
    this.tentouBloquear.set(false);
    this.bloqueioAberto.set(true);
  }

  protected bloquear(): void {
    this.tentouBloquear.set(true);
    const f = this.emBloqueio();
    if (!f || !this.motivo().trim()) return;
    this.salvando.set(true);
    this.financeiro.bloquearFornecedor(f.id, this.motivo().trim()).subscribe({
      next: () => {
        this.bloqueioAberto.set(false);
        this.concluir('Fornecedor bloqueado.');
      },
      error: (e) => this.falhar(e)
    });
  }

  protected async alternar(f: Fornecedor): Promise<void> {
    if (!f.ativo) return this.executar(f, 'ativar', 'Fornecedor ativado.');
    const ok = await this.confirmacao.confirm({
      title: 'Desativar fornecedor',
      message: `${f.nomeFantasia} deixa de aparecer em novas solicitações.`,
      confirmLabel: 'Desativar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.financeiro.desativarFornecedor(f.id).subscribe({ next: () => this.concluir('Fornecedor desativado.'), error: (e) => this.falhar(e) });
  }

  private concluir(mensagem: string): void {
    this.salvando.set(false);
    this.notificacoes.success(mensagem);
    this.lista.reload();
  }

  private falhar(e: unknown): void {
    this.salvando.set(false);
    this.notificacoes.error(getErrorMessage(e));
  }

  private temPermissao(permissao: string): boolean {
    return this.auth.currentUser()?.permissoes?.includes(permissao) ?? false;
  }
}
