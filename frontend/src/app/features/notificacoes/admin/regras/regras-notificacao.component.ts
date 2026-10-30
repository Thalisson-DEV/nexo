import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  CesButtonComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFilterPanelComponent,
  CesIconButtonComponent,
  CesListShortcutsDirective,
  CesPageHeaderComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTone,
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { catchError, forkJoin, of, tap } from 'rxjs';

import { PerfilAcessoResponse, UsuarioResponse } from '../../../../core/models/iam.models';
import { CategoriaNotificacao, GatilhoResponse, PrioridadeNotificacao, RegraNotificacaoResponse } from '../../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { NotificacoesService } from '../../../../core/services/notificacoes.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { AjudaListagemComponent } from '../../../../shared/listagem/ajuda-listagem.component';
import { LIMITE_EXPORTACAO, exportacaoPadrao, paginaPadrao } from '../../../../shared/listagem/listagem';
import { CATEGORIA_OPTIONS } from '../../notificacoes-ui';

const SITUACOES: CesSelectOption<boolean | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Ativas', value: true },
  { label: 'Inativas', value: false }
];
const ORIGENS: CesSelectOption<boolean>[] = [
  { label: 'De fábrica', value: true },
  { label: 'Criadas aqui', value: false }
];
const TOM_PRIORIDADE: Record<PrioridadeNotificacao, CesTone> = { BAIXA: 'neutral', NORMAL: 'info', ALTA: 'warning', CRITICA: 'danger' };

@Component({
  selector: 'app-regras-notificacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFilterPanelComponent,
    CesIconButtonComponent,
    CesListShortcutsDirective,
    CesPageHeaderComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page" cesListShortcuts #atalhos="cesListShortcuts" [newAction]="true" (newRequested)="nova()">
      <ces-page-header
        title="Regras de notificação"
        icon="pi pi-bell"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Comunicação' }, { label: 'Regras de notificação' }]"
        description="Quem é avisado, por qual canal e quando, para cada gatilho do sistema."
        helpLabel="Ajuda sobre regras de notificação"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Nova regra" [keyShortcut]="atalhos.newShortcut()" (pressed)="nova()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="notificacoes-regras" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Gatilho" [options]="gatilhoOpcoes()" [(value)]="lista.filters.gatilho" [filter]="true" [clearable]="true" placeholder="Todos"></ces-select>
          <ces-select label="Categoria" [options]="categorias" [(value)]="lista.filters.categoria" [clearable]="true" placeholder="Todas"></ces-select>
          <ces-select label="Origem" [options]="origens" [(value)]="lista.filters.sistema" [clearable]="true" placeholder="Todas"></ces-select>
          <ces-select label="Status" [options]="situacoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Regras"
        mode="server"
        storageKey="notificacoes-regras"
        exportFileName="regras-notificacao"
        searchPlaceholder="Nome ou descrição da regra"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova regra"
        noResultsTitle="Nenhuma regra encontrada com esse recorte."
        [columns]="colunas"
        [rows]="resultado.rows()"
        [totalRecords]="resultado.total()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as regras' : 'Nenhuma regra ativa'"
        [stateDescription]="resultado.error() ?? 'Crie uma regra ou remova o status nos filtros para ver as inativas.'"
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
        (emptyAction)="nova()"
        (rowActivate)="editar($event)"
      >
        <ng-template cesCell="nome" let-row>
          <span class="ces-cell-strong">{{ row.nome }}</span>
          @if (row.sistema) {
            <ces-status tone="neutral" emphasis="outline" icon="pi-lock">De fábrica</ces-status>
          }
          @if (row.descricao) {
            <span class="ces-cell-muted">{{ row.descricao }}</span>
          }
        </ng-template>
        <ng-template cesCell="gatilho" let-row>
          {{ row.gatilhoNome }}
          <code class="ces-cell-muted">{{ row.chaveGatilho }}</code>
        </ng-template>
        <ng-template cesCell="prioridade" let-row>
          <ces-status [tone]="tomPrioridade[row.prioridade]">{{ row.prioridade }}</ces-status>
        </ng-template>
        <ng-template cesCell="canais" let-row>
          <span [title]="rotuloCanalEmail(row)">{{ row.enviarEmail ? 'Sininho + e-mail' : 'Sininho' }}</span>
          @if (!row.enviarEmail) {
            <span class="ces-cell-muted">{{ rotuloCanalEmail(row) }}</span>
          }
        </ng-template>
        <ng-template cesCell="avisa" let-row>
          <span class="ces-cell-chips">
            @for (alvo of destinatarios(row); track $index) {
              <ces-status tone="neutral" emphasis="outline">{{ alvo }}</ces-status>
            }
          </span>
        </ng-template>
        <ng-template cesCell="disparo" let-row>
          {{ row.modo === 'POR_LIMIAR' ? 'A partir de ' + row.limiar + ' pendentes' : 'A cada ocorrência' }}
          <span class="ces-cell-muted">{{ silencio(row.cooldownMinutos) }}{{ row.condicoes.length ? ' · ' + row.condicoes.length + ' condição(ões)' : '' }}</span>
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome"
              size="icon-sm" (pressed)="alternar(row)"></ces-icon-button>
            @if (!row.sistema) {
              <ces-icon-button icon="pi pi-trash" variant="destructive" [label]="'Remover ' + row.nome" size="icon-sm" (pressed)="remover(row)"></ces-icon-button>
            }
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <app-ajuda-listagem #ajuda titulo="Regras de notificação" entidadePlural="regras" artigoPlural="as" busca="nome ou descrição" rotuloNovo="Nova regra">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-lock"></i> Regras de fábrica</h4>
        <p class="help-text-desc">Regras de fábrica podem ser editadas e desligadas, mas não removidas. Desativar uma regra para os avisos sem apagar o histórico.</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class RegrasNotificacaoComponent {
  private readonly servico = inject(NotificacoesService);
  private readonly usuarios = inject(UsuariosService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly router = inject(Router);

  protected readonly situacoes = SITUACOES;
  protected readonly origens = ORIGENS;
  protected readonly categorias = CATEGORIA_OPTIONS as CesSelectOption<CategoriaNotificacao>[];
  protected readonly tomPrioridade: Record<string, CesTone> = TOM_PRIORIDADE;
  protected readonly gatilhos = signal<GatilhoResponse[]>([]);
  protected readonly gatilhoOpcoes = computed<CesSelectOption<string>[]>(() => this.gatilhos().map((g) => ({ label: g.nome, value: g.chave })));
  private readonly perfis = signal<PerfilAcessoResponse[]>([]);
  private readonly listaUsuarios = signal<UsuarioResponse[]>([]);

  protected readonly lista = cesListState({
    storageKey: 'notificacoes-regras',
    filters: {
      gatilho: cesFilter.value<string | null>(null, { label: 'Gatilho', format: (chave): string | null => (chave ? this.nomeGatilho(chave) : null) }),
      categoria: cesFilter.value<CategoriaNotificacao | null>(null, { label: 'Categoria', options: () => CATEGORIA_OPTIONS as CesSelectOption<CategoriaNotificacao | null>[] }),
      sistema: cesFilter.value<boolean | null>(null, { label: 'Origem', type: 'boolean', options: () => ORIGENS as CesSelectOption<boolean | null>[] }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', options: () => SITUACOES })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<RegraNotificacaoResponse>(
    (pedido) =>
      this.servico
        .listarRegras({ ...this.filtro(), ...(paginaPadrao(pedido) as { page: number; size: number; sort?: string }) })
        .pipe(tap((p) => this.carregarUsuarios(p.content))),
    { errorMessage: getErrorMessage }
  );
  protected readonly limiteExportacao = LIMITE_EXPORTACAO;
  protected readonly exportar = exportacaoPadrao(
    (pagina) => this.servico.listarRegras({ ...this.filtro(), ...(pagina as { page: number; size: number; sort?: string }) }),
    () => this.lista.request()
  );

  protected readonly colunas: CesDataTableColumn<RegraNotificacaoResponse>[] = [
    { key: 'nome', header: 'Regra', type: 'custom', priority: 1, sortField: 'nome', value: (r) => r.nome, display: (r) => r.nome },
    { key: 'gatilho', header: 'Gatilho', type: 'custom', value: (r) => r.gatilhoNome, display: (r) => r.gatilhoNome },
    { key: 'categoria', header: 'Categoria', display: (r) => CATEGORIA_OPTIONS.find((c) => c.value === r.categoria)?.label ?? r.categoria },
    { key: 'prioridade', header: 'Prioridade', type: 'custom', value: (r) => r.prioridade, display: (r) => r.prioridade },
    { key: 'canais', header: 'Canais', type: 'custom', value: (r) => r.enviarEmail, display: (r) => (r.enviarEmail ? 'Sininho + e-mail' : 'Sininho') },
    { key: 'avisa', header: 'Avisa', type: 'custom', value: (r) => this.destinatarios(r).join(', '), display: (r) => this.destinatarios(r).join(', ') },
    { key: 'disparo', header: 'Disparo', type: 'custom', value: (r) => r.modo, display: (r) => (r.modo === 'POR_LIMIAR' ? `A partir de ${r.limiar} pendentes` : 'A cada ocorrência') },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, value: (r) => r.ativo, display: (r) => (r.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (r: RegraNotificacaoResponse) => r.nome;

  constructor() {
    this.servico.listarGatilhos().subscribe({ next: (g) => this.gatilhos.set(g), error: () => this.gatilhos.set([]) });
    this.usuarios.listarPerfisAtivos().subscribe({ next: (p) => this.perfis.set(p), error: () => this.perfis.set([]) });
  }

  private filtro() {
    const { filters, query } = this.lista.request();
    return {
      buscaGlobal: query || undefined,
      chaveGatilho: filters.gatilho ?? undefined,
      categoria: filters.categoria ?? undefined,
      sistema: filters.sistema ?? undefined,
      ativo: filters.ativo ?? undefined
    };
  }

  private nomeGatilho(chave: string): string {
    return this.gatilhos().find((g) => g.chave === chave)?.nome ?? chave;
  }

  protected nova(): void {
    void this.router.navigate(['/notificacoes/admin/regras/nova']);
  }

  protected editar(regra: RegraNotificacaoResponse): void {
    void this.router.navigate(['/notificacoes/admin/regras', regra.id, 'editar']);
  }

  protected destinatarios(regra: RegraNotificacaoResponse): string[] {
    return regra.destinatarios.map((d) => {
      if (d.descricaoAlvo) return d.descricaoAlvo;
      if (d.tipo === 'PERFIL') return this.perfis().find((p) => p.id === d.idAlvo)?.nome ?? 'Perfil';
      if (d.tipo === 'USUARIO') return this.listaUsuarios().find((u) => u.id === d.idAlvo)?.login ?? 'Usuário';
      return d.valorAlvo ?? d.tipoRotulo;
    });
  }

  /** O motivo de o canal estar desligado importa: veto da categoria ou escolha do administrador. */
  protected rotuloCanalEmail(regra: RegraNotificacaoResponse): string {
    if (regra.enviarEmail) return 'Também envia por e-mail';
    if (!regra.permiteEmail) return 'A categoria não aceita e-mail';
    return 'E-mail desligado nesta regra';
  }

  protected silencio(minutos: number): string {
    if (!minutos) return 'Sem silêncio';
    if (minutos % 60 !== 0) return `${minutos} min de silêncio`;
    const horas = minutos / 60;
    return horas === 1 ? '1 hora de silêncio' : `${horas} horas de silêncio`;
  }

  protected async alternar(regra: RegraNotificacaoResponse): Promise<void> {
    const ativar = !regra.ativo;
    const ok = await this.confirmacao.confirm({
      title: ativar ? 'Ativar regra' : 'Desativar regra',
      message: ativar ? `"${regra.nome}" volta a gerar notificações.` : `"${regra.nome}" deixa de gerar notificações, sem apagar o histórico.`,
      confirmLabel: ativar ? 'Ativar' : 'Desativar',
      cancelLabel: 'Voltar',
      variant: ativar ? 'default' : 'destructive'
    });
    if (!ok) return;
    this.servico.alternarRegra(regra.id, ativar).subscribe({
      next: () => {
        this.notificacoes.success(ativar ? 'Regra ativada.' : 'Regra desativada.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  /** Regra de fábrica é editável e desligável, nunca removível — o backend responde 422. */
  protected async remover(regra: RegraNotificacaoResponse): Promise<void> {
    if (regra.sistema) return;
    const ok = await this.confirmacao.confirm({
      title: 'Remover regra',
      message: `"${regra.nome}" será removida em definitivo.`,
      confirmLabel: 'Remover',
      cancelLabel: 'Voltar',
      variant: 'destructive'
    });
    if (!ok) return;
    this.servico.removerRegra(regra.id).subscribe({
      next: () => {
        this.notificacoes.success('Regra removida.');
        this.lista.reload();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  private carregarUsuarios(regras: readonly RegraNotificacaoResponse[]): void {
    const ids = regras.flatMap((r) => r.destinatarios.filter((d) => d.tipo === 'USUARIO' && !!d.idAlvo).map((d) => d.idAlvo as string));
    const carregados = new Set(this.listaUsuarios().map((u) => u.id));
    const faltantes = [...new Set(ids)].filter((id) => !carregados.has(id));
    if (!faltantes.length) return;
    forkJoin(faltantes.map((id) => this.usuarios.buscarUsuarioPorId(id).pipe(catchError(() => of(null))))).subscribe((lista) =>
      this.listaUsuarios.update((atuais) => [...atuais, ...lista.filter((u): u is UsuarioResponse => u !== null)])
    );
  }
}
