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
  NotificationService,
  cesFilter,
  cesListState
} from '@ces/ui';
import { forkJoin, map } from 'rxjs';

import { GatilhoResponse, PoliticaRoteamentoResponse, RotaRoteamentoResponse } from '../../../../core/models/notificacao.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { NotificacoesService } from '../../../../core/services/notificacoes.service';
import { AjudaListagemComponent } from '../../../../shared/listagem/ajuda-listagem.component';

const SITUACOES: CesSelectOption<boolean | null>[] = [
  { label: 'Todas', value: null },
  { label: 'Ativas', value: true },
  { label: 'Inativas', value: false }
];

/**
 * Políticas de roteamento de notificações. O endpoint devolve a lista completa (poucas políticas):
 * filtros, busca e paginação ficam na tabela; criar e editar têm rota própria (formulário longo).
 */
@Component({
  selector: 'app-politicas-roteamento',
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
        title="Políticas de roteamento"
        icon="pi pi-sitemap"
        [breadcrumb]="[{ label: 'Admin' }, { label: 'Comunicação' }, { label: 'Políticas de roteamento' }]"
        description="Regras que escolhem o destinatário de uma notificação conforme os dados da ocorrência."
        helpLabel="Ajuda sobre políticas de roteamento"
        (help)="ajuda.show()"
      >
        <ces-button actions icon="pi pi-plus" label="Nova política" [keyShortcut]="atalhos.newShortcut()" (pressed)="nova()"></ces-button>
      </ces-page-header>

      <ces-filter-panel #painel storageKey="notificacoes-politicas" [activeCount]="lista.activeCount()" [summary]="lista.summary()"
        [loading]="resultado.loading()" (applied)="lista.apply()" (cleared)="lista.reset()" (chipRemoved)="lista.remove($any($event))">
        <div class="ces-filter-grid">
          <ces-select label="Gatilho" [options]="gatilhoOpcoes()" [(value)]="lista.filters.gatilho" [filter]="true" [clearable]="true" placeholder="Todos"></ces-select>
          <ces-select label="Status" [options]="situacoes" [(value)]="lista.filters.ativo"></ces-select>
        </div>
      </ces-filter-panel>

      <ces-data-table
        title="Políticas"
        storageKey="notificacoes-politicas"
        exportFileName="politicas-roteamento"
        searchPlaceholder="Nome da política"
        rowActionLabel="Editar"
        rowActionIcon="pi pi-pencil"
        emptyActionLabel="Nova política"
        noResultsTitle="Nenhuma política encontrada com esse recorte."
        [columns]="colunas"
        [rows]="linhas()"
        [rowLabel]="rotulo"
        [state]="resultado.state()"
        [stateTitle]="resultado.error() ? 'Não foi possível carregar as políticas' : 'Nenhuma política cadastrada'"
        [stateDescription]="resultado.error() ?? undefined"
        [filtered]="lista.filtered()"
        [(query)]="lista.query"
        (adjustFilters)="painel.expandAndFocus()"
        (clearFilters)="lista.reset()"
        (emptyAction)="nova()"
        (rowActivate)="editar($event)"
      >
        <ng-template cesCell="nome" let-row>
          <span class="ces-cell-strong">{{ row.nome }}</span>
          @if (row.descricao) {
            <span class="ces-cell-muted">{{ row.descricao }}</span>
          }
        </ng-template>
        <ng-template cesCell="gatilho" let-row>
          {{ nomeGatilho(row.chaveGatilho) }}
          <code class="ces-cell-muted">{{ row.chaveGatilho }}</code>
        </ng-template>
        <ng-template cesCell="rotas" let-row>
          {{ condicionais(row).length }} {{ condicionais(row).length === 1 ? 'rota' : 'rotas' }}
          <span class="ces-cell-muted">{{ totalCondicoes(row) }} {{ totalCondicoes(row) === 1 ? 'condição' : 'condições' }}, avaliadas por ordem</span>
        </ng-template>
        <ng-template cesCell="padrao" let-row>
          @if (rotaPadrao(row); as padrao) {
            Configurado
            <span class="ces-cell-muted">Ordem {{ padrao.ordem }}</span>
          } @else {
            <ces-status tone="warning" icon="pi-exclamation-triangle">Não configurado</ces-status>
          }
        </ng-template>
        <ng-template cesCell="ativo" let-row>
          <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativa' : 'Inativa' }}</ces-status>
        </ng-template>
        <ng-template cesRowActions let-row>
          <span class="ces-table-actions">
            <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Ativar ') + row.nome"
              size="icon-sm" [loading]="alterandoId() === row.id" (pressed)="alternar(row)"></ces-icon-button>
          </span>
        </ng-template>
      </ces-data-table>
    </div>

    <app-ajuda-listagem #ajuda titulo="Políticas de roteamento" entidadePlural="políticas" artigoPlural="as" busca="nome da política" rotuloNovo="Nova política">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-sitemap"></i> Como a política escolhe o destinatário</h4>
        <p class="help-text-desc">As rotas condicionais são avaliadas da menor para a maior ordem; a primeira que atender todas as condições escolhe o destinatário. Sem nenhuma, vale a rota "caso contrário".</p>
      </div>
    </app-ajuda-listagem>
  `
})
export class PoliticasRoteamentoComponent {
  private readonly servico = inject(NotificacoesService);
  private readonly router = inject(Router);
  private readonly confirmacao = inject(CesConfirmationService);
  private readonly notificacoes = inject(NotificationService);

  protected readonly situacoes = SITUACOES;
  protected readonly gatilhos = signal<GatilhoResponse[]>([]);
  protected readonly gatilhoOpcoes = computed<CesSelectOption<string>[]>(() =>
    this.gatilhos().filter((g) => g.modo === 'POR_OCORRENCIA').map((g) => ({ label: g.nome, value: g.chave }))
  );
  protected readonly alterandoId = signal<string | null>(null);
  /** Políticas alteradas nesta tela (ativar/desativar devolve a política atualizada). */
  private readonly atualizadas = signal<ReadonlyMap<string, PoliticaRoteamentoResponse>>(new Map());

  protected readonly lista = cesListState({
    storageKey: 'notificacoes-politicas',
    filters: {
      gatilho: cesFilter.value<string | null>(null, { label: 'Gatilho', format: (chave): string | null => (chave ? this.nomeGatilho(chave) : null) }),
      ativo: cesFilter.value<boolean | null>(true, { label: 'Status', options: () => SITUACOES })
    },
    sort: [{ key: 'nome', direction: 'asc' }]
  });
  protected readonly resultado = this.lista.results<PoliticaRoteamentoResponse>(
    () =>
      forkJoin({ politicas: this.servico.listarPoliticas(), gatilhos: this.servico.listarGatilhos() }).pipe(
        map(({ politicas, gatilhos }) => {
          this.gatilhos.set(gatilhos);
          this.atualizadas.set(new Map());
          return politicas;
        })
      ),
    { immediate: true, errorMessage: getErrorMessage }
  );
  protected readonly linhas = computed(() => {
    const { gatilho, ativo } = this.lista.applied();
    const alteradas = this.atualizadas();
    return this.resultado
      .rows()
      .map((p) => alteradas.get(p.id) ?? p)
      .filter((p) => (!gatilho || p.chaveGatilho === gatilho) && (ativo === null || p.ativo === ativo));
  });

  protected readonly colunas: CesDataTableColumn<PoliticaRoteamentoResponse>[] = [
    { key: 'nome', header: 'Política', type: 'custom', priority: 1, value: (p) => p.nome, display: (p) => `${p.nome} ${p.descricao ?? ''}` },
    { key: 'gatilho', header: 'Gatilho', type: 'custom', value: (p) => this.nomeGatilho(p.chaveGatilho), display: (p) => this.nomeGatilho(p.chaveGatilho) },
    { key: 'rotas', header: 'Rotas condicionais', type: 'custom', value: (p) => this.condicionais(p).length, display: (p) => `${this.condicionais(p).length} rotas` },
    { key: 'padrao', header: 'Caso contrário', type: 'custom', value: (p) => !!this.rotaPadrao(p), display: (p) => (this.rotaPadrao(p) ? 'Configurado' : 'Não configurado') },
    { key: 'ativo', header: 'Status', type: 'custom', priority: 1, value: (p) => p.ativo, display: (p) => (p.ativo ? 'Ativa' : 'Inativa') }
  ];
  protected readonly rotulo = (p: PoliticaRoteamentoResponse) => p.nome;

  protected nova(): void {
    void this.router.navigate(['/notificacoes/admin/politicas-roteamento/nova']);
  }

  protected editar(politica: PoliticaRoteamentoResponse): void {
    void this.router.navigate(['/notificacoes/admin/politicas-roteamento', politica.id, 'editar']);
  }

  protected nomeGatilho(chave: string): string {
    return this.gatilhos().find((g) => g.chave === chave)?.nome ?? chave;
  }

  protected condicionais(politica: PoliticaRoteamentoResponse): RotaRoteamentoResponse[] {
    return politica.rotas.filter((rota) => !rota.padrao);
  }

  protected totalCondicoes(politica: PoliticaRoteamentoResponse): number {
    return this.condicionais(politica).reduce((total, rota) => total + rota.condicoes.length, 0);
  }

  protected rotaPadrao(politica: PoliticaRoteamentoResponse): RotaRoteamentoResponse | null {
    return politica.rotas.find((rota) => rota.padrao) ?? null;
  }

  protected async alternar(politica: PoliticaRoteamentoResponse): Promise<void> {
    const ativar = !politica.ativo;
    const ok = await this.confirmacao.confirm({
      title: ativar ? 'Ativar política' : 'Desativar política',
      message: ativar
        ? `“${politica.nome}” poderá voltar a ser escolhida nas regras compatíveis.`
        : `“${politica.nome}” deixará de aceitar novas associações. Regras existentes precisarão de revisão antes de serem salvas.`,
      confirmLabel: ativar ? 'Ativar' : 'Desativar',
      cancelLabel: 'Voltar',
      variant: ativar ? 'default' : 'destructive'
    });
    if (!ok) return;
    this.alterandoId.set(politica.id);
    this.servico.alternarPolitica(politica.id, ativar).subscribe({
      next: (atualizada) => {
        this.atualizadas.update((mapa) => new Map(mapa).set(atualizada.id, atualizada));
        this.alterandoId.set(null);
        this.notificacoes.success(ativar ? 'Política ativada.' : 'Política desativada.');
      },
      error: (e) => {
        this.alterandoId.set(null);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }
}
