import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import {
  ThemeService,
  CES_PRIMARY_COLORS,
  CesPrimaryColor,
} from '../../core/services/theme.service';
import { CesAsyncStateComponent, CesCalloutComponent } from '../feedback';
import {
  CesAutocompleteComponent,
  CesBooleanControlComponent,
  CesFieldComponent,
  CesFileInputComponent,
  CesPasswordInputComponent,
} from '../forms';
import {
  CesButtonComponent,
  CesIconButtonComponent,
  CesStatusComponent,
  CesSurfaceComponent,
} from '../primitives';
import { CesBlockingProgressDialogComponent } from '../overlays';
import { DesignSystemCatalogListPageComponent } from './design-system-catalog-list-page.component';
import { CesChartComponent } from '../patterns/chart';
import {
  CES_CHART_CATALOG_LABELS,
  CES_CHART_CATALOG_SERIES,
} from '../patterns/chart/chart.catalog.fixture';

@Component({
  selector: 'ces-design-system-catalog',
  standalone: true,
  imports: [
    FormsModule,
    InputTextModule,
    SelectModule,
    CesAutocompleteComponent,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesBlockingProgressDialogComponent,
    CesCalloutComponent,
    CesChartComponent,
    CesBooleanControlComponent,
    CesFieldComponent,
    CesFileInputComponent,
    CesIconButtonComponent,
    CesPasswordInputComponent,
    CesStatusComponent,
    CesSurfaceComponent,
    DesignSystemCatalogListPageComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ces-catalog">
      <aside class="ces-catalog__rail">
        <div class="ces-catalog__identity">
          <span class="ces-catalog__mark" aria-hidden="true"><i class="pi pi-bolt"></i></span>
          <div>
            <strong>Nexo UI</strong>
            <span>Contrato visual</span>
          </div>
        </div>

        <nav aria-label="Seções do catálogo">
          <a href="#acoes">Ações</a>
          <a href="#campos">Campos</a>
          <a href="#status">Status</a>
          <a href="#estados">Estados</a>
          <a href="#listagem">Listagem</a>
          <a href="#patterns">Patterns</a>
          <a href="#graficos">Gráficos</a>
        </nav>

        <p>
          A faixa lateral replica a lógica operacional do sistema: sinal primeiro, conteúdo depois.
          Ela é a assinatura do catálogo, não uma decoração para todas as telas.
        </p>
      </aside>

      <main class="ces-catalog__content">
        <header class="ces-catalog__header">
          <div>
            <span class="ces-catalog__eyebrow">Design System Nexo</span>
            <h1>Uma linguagem única para trabalho de campo e backoffice.</h1>
            <p>
              Componentes source-owned sobre PrimeNG, com densidade compacta, estados offline
              explícitos e APIs por intenção — nunca por cor.
            </p>
          </div>

          <div class="ces-catalog__controls" aria-label="Preferências de visualização">
            <label>
              <span>Cor primária</span>
              <p-select
                [autofocus]="false"
                [options]="primaryColors"
                [(ngModel)]="selectedColor"
                (onChange)="setPrimaryColor($event.value)"
                appendTo="body"
                styleClass="ces-prime-control"
              ></p-select>
            </label>
            <ces-button
              [label]="theme.isDarkMode() ? 'Usar tema claro' : 'Usar tema escuro'"
              [icon]="theme.isDarkMode() ? 'pi pi-sun' : 'pi pi-moon'"
              variant="outline"
              (pressed)="theme.setDarkMode(!theme.isDarkMode())"
            ></ces-button>
            <ces-button
              [label]="
                theme.density() === 'compact' ? 'Densidade confortável' : 'Densidade compacta'
              "
              icon="pi pi-sliders-h"
              variant="ghost"
              (pressed)="toggleDensity()"
            ></ces-button>
          </div>
        </header>

        <section id="acoes" class="ces-catalog__section" aria-labelledby="acoes-title">
          <div class="ces-catalog__section-heading">
            <span>Controle</span>
            <div>
              <h2 id="acoes-title">Ações</h2>
              <p>O texto descreve o resultado. Cor é implementação, intenção é contrato.</p>
            </div>
          </div>

          <ces-surface variant="raised">
            <div class="ces-catalog__row">
              <ces-button label="Salvar alterações" icon="pi pi-check"></ces-button>
              <ces-button label="Processar lote" icon="pi pi-bolt" variant="success"></ces-button>
              <ces-button label="Cancelar" icon="pi pi-times" variant="secondary"></ces-button>
              <ces-button
                label="Excluir registro"
                icon="pi pi-trash"
                variant="destructive"
              ></ces-button>
              <ces-button label="Carregando" icon="pi pi-save" [loading]="true"></ces-button>
              <ces-button label="Ver processamento modal" icon="pi pi-clock" variant="secondary" (pressed)="showBlockingProgress()"></ces-button>
              <ces-icon-button icon="pi pi-pencil" label="Editar registro"></ces-icon-button>
              <ces-icon-button
                icon="pi pi-trash"
                label="Excluir registro"
                variant="destructive"
              ></ces-icon-button>
            </div>
          </ces-surface>
        </section>

        <section id="campos" class="ces-catalog__section" aria-labelledby="campos-title">
          <div class="ces-catalog__section-heading">
            <span>Entrada</span>
            <div>
              <h2 id="campos-title">Campos</h2>
              <p>Rótulo, ajuda, erro e controle compartilham o mesmo identificador e contexto.</p>
            </div>
          </div>

          <div class="ces-catalog__grid">
            <ces-surface variant="outlined">
              <ces-field
                label="Base operacional"
                inputId="catalog-base"
                description="Selecione a base responsável pela operação."
                [required]="true"
              >
                <input
                  id="catalog-base"
                  pInputText
                  class="ces-prime-control"
                  placeholder="Digite para pesquisar"
                  aria-describedby="catalog-base-description"
                />
              </ces-field>
            </ces-surface>

            <ces-surface variant="outlined">
              <ces-autocomplete
                label="Colaborador"
                inputId="catalog-collaborator"
                description="Selecione o nome correto na lista de resultados."
                [options]="catalogCollaboratorOptions()"
                [status]="catalogCollaboratorStatus()"
                (search)="searchCatalogCollaborators($event)"
              ></ces-autocomplete>
            </ces-surface>

            <ces-surface variant="outlined">
              <ces-file-input
                label="Planilha de solicitações"
                description="XLSX ou CSV, até 5 MiB. A seleção não inicia o upload."
                accept=".xlsx,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/csv"
                [maxBytes]="5 * 1024 * 1024"
                [file]="catalogFile()"
                (fileChange)="catalogFile.set($event)"
                [showOptional]="true"
              ></ces-file-input>
            </ces-surface>

            <ces-surface variant="outlined">
              <ces-password-input
                label="Senha inicial"
                inputId="catalog-password"
                description="Mínimo de 6 caracteres. O botão revela o que foi digitado."
                [minlength]="6"
                [required]="true"
              ></ces-password-input>
            </ces-surface>

            <ces-surface variant="outlined">
              <ces-boolean-control
                label="Saque fora da equipe"
                inputId="catalog-outside-team"
                description="Permite selecionar um colaborador externo."
                kind="toggle"
                [inline]="true"
              ></ces-boolean-control>
            </ces-surface>

            <ces-surface variant="outlined">
              <ces-field
                label="Centro de custo"
                inputId="catalog-cost-center"
                error="Selecione um centro de custo válido."
                [required]="true"
              >
                <input
                  id="catalog-cost-center"
                  pInputText
                  class="ces-prime-control"
                  value="Centro encerrado"
                  aria-invalid="true"
                  aria-describedby="catalog-cost-center-error"
                />
              </ces-field>
            </ces-surface>
          </div>
        </section>

        <section id="status" class="ces-catalog__section" aria-labelledby="status-title">
          <div class="ces-catalog__section-heading">
            <span>Sinal</span>
            <div>
              <h2 id="status-title">Status</h2>
              <p>O domínio mapeia seus estados para tons semânticos estáveis.</p>
            </div>
          </div>

          <ces-surface variant="subtle">
            <div class="ces-catalog__row">
              <ces-status tone="neutral">Rascunho</ces-status>
              <ces-status tone="brand" icon="pi-circle-fill">Em análise</ces-status>
              <ces-status tone="info">Informativo</ces-status>
              <ces-status tone="success" icon="pi-check">Concluído</ces-status>
              <ces-status tone="warning" icon="pi-clock">Aguardando sincronização</ces-status>
              <ces-status tone="danger" icon="pi-times">Rejeitado</ces-status>
            </div>
          </ces-surface>
        </section>

        <section id="estados" class="ces-catalog__section" aria-labelledby="estados-title">
          <div class="ces-catalog__section-heading">
            <span>Resposta</span>
            <div>
              <h2 id="estados-title">Estados de conteúdo</h2>
              <p>Vazio, erro e cache offline orientam ações diferentes.</p>
            </div>
          </div>

          <div class="ces-catalog__states">
            <ces-surface variant="outlined" [padded]="false">
              <ces-async-state
                state="empty"
                title="Nenhum colaborador cadastrado"
                description="Cadastre o primeiro colaborador para montar as equipes."
                actionLabel="Cadastrar colaborador"
                actionIcon="pi pi-plus"
              ></ces-async-state>
            </ces-surface>
            <ces-surface variant="outlined" [padded]="false">
              <ces-async-state
                state="error"
                actionLabel="Tentar novamente"
                actionIcon="pi pi-refresh"
              ></ces-async-state>
            </ces-surface>
            <ces-surface variant="outlined" [padded]="false">
              <ces-async-state
                state="offline-cached"
                actionLabel="Ver pendências"
                actionIcon="pi pi-list"
              ></ces-async-state>
            </ces-surface>
          </div>

          <ces-surface variant="raised">
            <div class="flex flex-col gap-3">
              <strong>CesCalloutComponent</strong>
              <p class="text-xs text-[var(--ces-muted)]">
                Mensagem contextual para conteúdo instrucional. O tom sempre vem acompanhado de um
                rótulo textual, então a cor nunca é o único indicador, e o bloco continua legível
                em papel.
              </p>

              <ces-callout tone="brand" label="Objetivo" icon="pi-bullseye">
                Registrar a inspeção no local, mesmo sem sinal de internet.
              </ces-callout>

              <ces-callout tone="info" title="Instrução de segurança">
                A senha provisória deve ser alterada no primeiro acesso.
              </ces-callout>

              <ces-callout tone="warning" title="Lista de itens congelada">
                Depois de validar, nenhum material pode ser adicionado ou removido.
              </ces-callout>

              <ces-callout tone="danger" title="Exportação indisponível">
                Não foi possível gerar o arquivo. Tente novamente em alguns minutos.
              </ces-callout>

              <ces-callout tone="success" label="Dica" icon="pi-lightbulb" [dense]="true">
                Pressione Ctrl + K para abrir a busca global em qualquer tela.
              </ces-callout>
            </div>
          </ces-surface>
        </section>

        <section id="listagem" class="ces-catalog__section" aria-labelledby="listagem-title">
          <div class="ces-catalog__section-heading">
            <span>Padrão</span>
            <div>
              <h2 id="listagem-title">Listagem (tabela primeiro)</h2>
              <p>
                ces-page-header + ces-filter-panel recolhido com resumo + ces-data-table em modo
                servidor + ces-form-drawer + atalhos (/ busca, F filtros, N novo). Servidor simulado
                com 240 registros. Veja docs/design-system/components/list-page.md.
              </p>
            </div>
          </div>

          <ces-catalog-list-page></ces-catalog-list-page>
        </section>

        <section id="patterns" class="ces-catalog__section" aria-labelledby="patterns-title">
          <div class="ces-catalog__section-heading">
            <span>Composição</span>
            <div>
              <h2 id="patterns-title">Patterns</h2>
              <p>Soluções estruturais e navegação lateral padronizada em relatórios.</p>
            </div>
          </div>

          <div class="ces-catalog__states">
            <ces-surface variant="outlined" [padded]="true">
              <div class="flex flex-col gap-3">
                <div class="flex items-center gap-2">
                  <i class="pi pi-compass text-[var(--ces-primary-600)]"></i>
                  <strong>CesReportNavigatorComponent</strong>
                </div>
                <p class="text-xs text-[var(--ces-muted)]">
                  Navegador lateral recolhível (rail 56px / sidebar 384px) para relatórios em tela
                  cheia. Suporta busca com debounce, filtros de escopo/status, ordenação, atalho
                  Ctrl+K, paginação virtual/infinita e estado sincronizado.
                </p>
                <div class="flex items-center gap-2">
                  <ces-status tone="brand">@ces/ui/patterns</ces-status>
                  <ces-status tone="success">10 Domínios Integrados</ces-status>
                </div>
              </div>
            </ces-surface>
          </div>
        </section>

        <section id="graficos" class="ces-catalog__section" aria-labelledby="graficos-title">
          <div class="ces-catalog__section-heading">
            <span>Dados</span>
            <div>
              <h2 id="graficos-title">Gráficos</h2>
              <p>
                Séries com cores de token, números em pt-BR, resumo para leitores de tela e tabela
                de dados equivalente. Sem cor como único indicador.
              </p>
            </div>
          </div>

          <div class="ces-catalog__states">
            <ces-surface variant="outlined" [padded]="true">
              <ces-chart
                kind="bar"
                title="Movimentações por mês"
                description="Barras agrupadas com duas séries."
                [labels]="chartLabels"
                [series]="chartSeries"
                valueFormat="integer"
                [height]="220"
                [animated]="false"
              ></ces-chart>
            </ces-surface>
            <ces-surface variant="outlined" [padded]="true">
              <ces-chart
                kind="line"
                title="Tendência de saídas"
                [labels]="chartLabels"
                [series]="chartSeriesSaidas"
                valueFormat="integer"
                [height]="220"
                [animated]="false"
              ></ces-chart>
            </ces-surface>
            <ces-surface variant="outlined" [padded]="true">
              <ces-chart
                kind="doughnut"
                title="Composição por tipo"
                [labels]="chartComposicaoLabels"
                [series]="chartComposicao"
                valueFormat="integer"
                [height]="220"
                [animated]="false"
              ></ces-chart>
            </ces-surface>
            <ces-surface variant="outlined" [padded]="true">
              <ces-chart
                kind="bar-horizontal"
                title="Valor por fornecedor"
                [labels]="chartFornecedores"
                [series]="chartValores"
                valueFormat="currency"
                [height]="220"
                [animated]="false"
                [dataTableVisible]="true"
                note="Exibido como tabela de dados (alternativa acessível)."
              ></ces-chart>
            </ces-surface>
            <ces-surface variant="outlined" [padded]="true">
              <ces-chart kind="bar" title="Sem dados no período" [labels]="[]" [series]="[]"></ces-chart>
            </ces-surface>
          </div>
        </section>
      </main>
    </div>
    <ces-blocking-progress-dialog
      [visible]="blockingProgressVisible()"
      title="Processando lote"
      statusTitle="Confirmando operação"
      statusDescription="Você verá o resultado assim que terminar."
    ></ces-blocking-progress-dialog>
  `,
  styleUrl: './design-system-catalog.component.css',
})
export class DesignSystemCatalogComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly primaryColors = CES_PRIMARY_COLORS.map((color) => ({
    label: color,
    value: color,
  }));
  protected readonly selectedColor = signal<CesPrimaryColor>(this.theme.primaryColor());
  protected readonly catalogFile = signal<File | null>(null);
  protected readonly blockingProgressVisible = signal(false);
  protected readonly chartLabels = CES_CHART_CATALOG_LABELS;
  protected readonly chartSeries = CES_CHART_CATALOG_SERIES;
  protected readonly chartSeriesSaidas = CES_CHART_CATALOG_SERIES.slice(1);
  protected readonly chartComposicaoLabels = ['Entrega', 'Devolução', 'Transferência', 'Estorno'];
  protected readonly chartComposicao = [{ name: 'Movimentações', values: [48, 21, 9, 6] }];
  protected readonly chartFornecedores = ['Fornecedor Alfa', 'Fornecedor Beta', 'Fornecedor Gama'];
  protected readonly chartValores = [{ name: 'Valor', values: [48750.9, 23110, 9800.5] }];
  private readonly catalogCollaborators = [
    { label: 'Ana Ribeiro', value: 'collaborator-1' },
    { label: 'Bruno Costa', value: 'collaborator-2' },
  ];
  protected readonly catalogCollaboratorOptions = signal(this.catalogCollaborators);
  protected readonly catalogCollaboratorStatus = signal<'idle' | 'success' | 'empty'>('idle');

  protected setPrimaryColor(color: CesPrimaryColor) {
    this.selectedColor.set(color);
    this.theme.updatePrimaryColor(color);
  }

  protected toggleDensity() {
    this.theme.setDensity(this.theme.density() === 'compact' ? 'comfortable' : 'compact');
  }

  protected showBlockingProgress(): void {
    this.blockingProgressVisible.set(true);
    window.setTimeout(() => this.blockingProgressVisible.set(false), 1800);
  }

  protected searchCatalogCollaborators(query: string): void {
    const normalized = query.trim().toLocaleLowerCase('pt-BR');
    if (normalized.length < 2) {
      this.catalogCollaboratorOptions.set([]);
      this.catalogCollaboratorStatus.set('idle');
      return;
    }

    const options = this.catalogCollaborators.filter((option) =>
      option.label.toLocaleLowerCase('pt-BR').includes(normalized),
    );
    this.catalogCollaboratorOptions.set(options);
    this.catalogCollaboratorStatus.set(options.length ? 'success' : 'empty');
  }
}
