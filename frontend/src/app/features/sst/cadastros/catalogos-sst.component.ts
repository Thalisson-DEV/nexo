import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
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

import { getErrorMessage } from '../../../core/models/shared.models';
import { CatalogoSstResponse, TipoCatalogoSst } from '../../../core/models/sst-evento.models';
import { AuthService } from '../../../core/services/auth.service';
import { SstService } from '../../../core/services/sst.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../shared/listagem/listagem';
import { CATALOGO_SST_PAGE_CONFIG } from './catalogo-sst-page.config';

const SITUACOES: CesSelectOption<boolean | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Ativos', value: true },
  { label: 'Desativados', value: false }
];

/**
 * Cadastros do catálogo SST (classificações, locais e naturezas de lesão, agentes causadores).
 * A tabela é genérica no backend; a rota fixa o tipo e cada vocabulário aparece como um cadastro próprio.
 */
@Component({
  selector: 'app-sst-catalogos',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="podeEscrever" (newRequested)="novo()">
      <ces-page-header
        [title]="config.titulo"
        [icon]="config.icone"
        [breadcrumb]="[{ label: 'SST' }, { label: 'Cadastros' }, { label: config.titulo }]"
        [description]="config.nomeAjuda"
        [helpLabel]="'Ajuda sobre ' + config.titulo.toLowerCase()"
        (help)="ajuda.show()"
      >
        @if (podeEscrever) {
          <ces-button actions icon="pi pi-plus" [label]="config.entityLabel" [keyShortcut]="atalhos.newShortcut()" (pressed)="novo()"></ces-button>
        }
      </ces-page-header>

      <ces-filter-panel #painel [storageKey]="'sst-catalogo-' + tipo" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Situação" [options]="situacoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        [title]="config.titulo"
        mode="server"
        [storageKey]="'sst-catalogo-' + tipo"
        [exportFileName]="'sst-' + tipo.toLowerCase()"
        [searchPlaceholder]="config.colunaNome + ', descrição ou código'"
        [rowActionLabel]="podeEscrever ? 'Editar' : undefined"
        rowActionIcon="pi pi-pencil"
        [emptyActionLabel]="podeEscrever ? config.entityLabel : undefined"
        [noResultsTitle]="config.vazio + ' com esse recorte.'"
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar ' + config.titulo.toLowerCase() : config.vazio"
        [stateDescription]="resultado.error() ?? (lista.applied().ativo ? 'A lista mostra só os ativos; remova a situação nos filtros para ver os desativados.' : undefined)"
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
          <span class="ces-cell-strong">{{ row.nome }}</span>
          <span class="ces-cell-muted">{{ row.descricao || '—' }}</span>
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Desativado' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          @if (podeEscrever) {
            <span class="ces-table-actions">
              <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Reativar ') + row.nome"
                size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            </span>
          }
        </ng-template>
      </ces-data-table>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="emEdicao() ? config.dialogEditar : config.dialogNovo"
      [description]="emEdicao()?.nome" [dirty]="alterado()" size="md">
      <ces-form-section [title]="config.colunaNome" [icon]="config.icone">
        <ces-text-input [label]="config.nomeLabel" [required]="true" [(value)]="nome" [maxlength]="120" [placeholder]="config.nomePlaceholder"
          [description]="config.nomeAjuda" [error]="tentouSalvar() && !nome().trim() ? 'Campo obrigatório.' : undefined"></ces-text-input>
        <ces-textarea label="Descrição" [(value)]="descricao" [rows]="3" [maxlength]="255" [showOptional]="true"
          placeholder="Explique quando esta opção deve ser usada"></ces-textarea>
        <div class="ces-form-grid">
          <ces-text-input label="Código externo" [(value)]="codigo" [maxlength]="30" [showOptional]="true" placeholder="Ex.: código eSocial"
            description="Só quando houver referência normativa ou de integração."></ces-text-input>
          <ces-number-input label="Ordem de exibição" [(value)]="ordem" [min]="0" [useGrouping]="false" description="Menores números aparecem primeiro."></ces-number-input>
        </div>
      </ces-form-section>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : config.botaoCadastrar" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>

    <app-ajuda-listagem #ajuda [titulo]="config.titulo" [entidadePlural]="config.titulo.toLowerCase()" artigoPlural="os"
      [busca]="config.colunaNome.toLowerCase() + ', descrição ou código'" [rotuloNovo]="config.entityLabel" [podeCriar]="podeEscrever">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-info-circle"></i> Desativar</h4>
        <p class="help-text-desc">Desativar tira a opção das novas seleções, mas mantém o texto nos eventos que já a usam.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class CatalogosSstComponent {
  private readonly sst = inject(SstService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  protected readonly podeEscrever = inject(AuthService).currentUser()?.permissoes?.includes('sst:catalogo:write') ?? false;

  protected readonly tipo = inject(ActivatedRoute).snapshot.data['tipoCatalogo'] as TipoCatalogoSst;
  protected readonly config = CATALOGO_SST_PAGE_CONFIG[this.tipo];
  protected readonly situacoes = SITUACOES;

  protected readonly lista = cesListState({
    storageKey: `sst-catalogo-${this.tipo}`,
    filters: { ativo: cesFilter.value<boolean | null>(true, { label: 'Situação', param: 'situacao', options: () => SITUACOES }) },
    sort: [{ key: 'ordem', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<CatalogoSstResponse>(
    (pedido) => this.sst.listarCatalogosSst({ ...this.filtro(), ...paginaPadrao(pedido) }),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao((pagina) => this.sst.listarCatalogosSst({ ...this.filtro(), ...pagina }), () => this.lista.request());

  protected readonly colunas: CesDataTableColumn<CatalogoSstResponse>[] = [
    { key: 'nome', header: this.config.colunaNome, type: 'custom', priority: 1, sortField: 'nome', value: (c) => c.nome, display: (c) => `${c.nome} ${c.descricao ?? ''}` },
    { key: 'codigo', header: 'Código externo', display: (c) => c.codigo || '—' },
    { key: 'ativo', header: 'Situação', type: 'custom', priority: 1, value: (c) => c.ativo, display: (c) => (c.ativo ? 'Ativo' : 'Desativado') },
    { key: 'ordem', header: 'Ordem', type: 'integer', sortField: 'ordem' }
  ];
  protected readonly rotulo = (c: CatalogoSstResponse) => c.nome;

  protected readonly formularioAberto = signal(false);
  protected readonly emEdicao = signal<CatalogoSstResponse | null>(null);
  protected readonly nome = signal('');
  protected readonly descricao = signal('');
  protected readonly codigo = signal('');
  protected readonly ordem = signal<number | null>(0);
  protected readonly salvando = signal(false);
  protected readonly tentouSalvar = signal(false);
  protected readonly alterado = computed(() => {
    const c = this.emEdicao();
    return c
      ? this.nome() !== c.nome || this.descricao() !== (c.descricao ?? '') || this.codigo() !== (c.codigo ?? '') || this.ordem() !== c.ordem
      : !!(this.nome() + this.descricao() + this.codigo()).trim();
  });

  private filtro() {
    const { filters, query } = this.lista.request();
    return { tipo: this.tipo, ativo: filters.ativo, buscaGlobal: query || null };
  }

  protected novo(): void {
    if (!this.podeEscrever) return;
    this.preencher(null);
    this.formularioAberto.set(true);
  }

  protected editar(item: CatalogoSstResponse): void {
    this.preencher(item);
    this.formularioAberto.set(true);
  }

  private preencher(c: CatalogoSstResponse | null): void {
    this.emEdicao.set(c);
    this.nome.set(c?.nome ?? '');
    this.descricao.set(c?.descricao ?? '');
    this.codigo.set(c?.codigo ?? '');
    this.ordem.set(c?.ordem ?? 0);
    this.tentouSalvar.set(false);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (this.salvando() || !this.nome().trim()) return;
    const atual = this.emEdicao();
    const request = {
      tipo: this.tipo,
      nome: this.nome().trim(),
      descricao: this.descricao().trim() || null,
      codigo: this.codigo().trim() || null,
      ordem: this.ordem() ?? 0
    };
    this.salvando.set(true);
    (atual ? this.sst.atualizarCatalogoSst(atual.id, request) : this.sst.criarCatalogoSst(request)).subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(`${request.nome} salvo.`);
        this.lista.reload();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  /** Desativar mantém a opção legível nos eventos que já a referenciam. */
  protected async alternar(item: CatalogoSstResponse): Promise<void> {
    const desativar = item.ativo;
    const ok = await this.confirmacao.confirm({
      title: desativar ? 'Desativar opção' : 'Reativar opção',
      message: desativar
        ? `${item.nome} sai das novas seleções. Os eventos que já a usam continuam mostrando o texto.`
        : `${item.nome} volta a aparecer nas seleções.`,
      confirmLabel: desativar ? 'Desativar' : 'Reativar',
      variant: desativar ? 'destructive' : 'default'
    });
    if (!ok) return;
    (desativar ? this.sst.desativarCatalogoSst(item.id) : this.sst.ativarCatalogoSst(item.id)).subscribe({
      next: () => {
        this.notificacoes.success(desativar ? `${item.nome} desativado.` : `${item.nome} reativado.`);
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }
}
