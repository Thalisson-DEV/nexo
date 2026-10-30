import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, output, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFileInputComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTone,
  NotificationService
} from '@ces/ui';
import { Subscription, filter, switchMap, take, timer } from 'rxjs';

import {
  ErroLinhaImportacao,
  HistoricoImportacaoObraResponse,
  StatusImportacaoObra,
  TipoImportacaoObra
} from '../../../core/models/obras-kit.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { formatarDataHora } from '../obras-ui';

/** Intervalo de consulta do andamento enquanto a importação está em processamento. */
export const INTERVALO_ACOMPANHAMENTO_MS = 2000;

const NOME_MODELO: Record<TipoImportacaoObra, string> = {
  KITS: 'modelo-importacao-kits.xlsx',
  CARTEIRA: 'modelo-importacao-carteira.xlsx',
  ORCAMENTO: 'modelo-importacao-orcamento.xlsx'
};

export function situacaoImportacao(status: StatusImportacaoObra, erros = 0): { label: string; tone: CesTone; icon: string } {
  switch (status) {
    case 'PROCESSANDO':
      return { label: 'Processando', tone: 'info', icon: 'pi-spin pi-spinner' };
    case 'FALHOU':
      return { label: 'Falhou', tone: 'danger', icon: 'pi-times-circle' };
    default:
      return erros > 0
        ? { label: 'Concluída com erros', tone: 'warning', icon: 'pi-exclamation-triangle' }
        : { label: 'Concluída', tone: 'success', icon: 'pi-check-circle' };
  }
}

/**
 * Importação de planilha de Obras processada no servidor em segundo plano (kits, carteira,
 * orçamento previsto): envia o arquivo, acompanha o andamento, mostra o relatório por linha e o
 * histórico das importações do mesmo tipo. Fechar o drawer não interrompe o processamento.
 */
@Component({
  selector: 'app-importacao-obras-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFileInputComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesSelectComponent,
    CesStatusComponent
  ],
  styles: `
    .importacao__resumo {
      margin: 0.75rem 0;
    }

    .importacao__cabecalho {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
  `,
  template: `
    <ces-form-drawer [(visible)]="visible" [title]="titulo()" [description]="descricao()" size="lg" (closed)="aoFechar()">
      <ces-form-section title="Nova importação" icon="pi pi-upload">
        <ces-callout tone="info" [dense]="true">
          A planilha é processada em segundo plano, uma linha por vez. Linhas com erro ficam no relatório e não impedem as demais.
          Você pode fechar esta janela; o resultado fica no histórico.
        </ces-callout>
        <div class="ces-form-grid">
          @if (exigeContrato()) {
            <ces-select label="Contrato" [required]="true" [options]="contratos()" [(value)]="idContrato" [filter]="true"
              placeholder="Selecione o contrato" [error]="erroContrato()"></ces-select>
          }
          <ces-file-input label="Planilha" [required]="true" accept=".xlsx,.csv" [(file)]="arquivo"
            description="XLSX ou CSV com cabeçalho na primeira linha." [maxBytes]="limiteBytes"></ces-file-input>
        </div>
        <div class="ces-actions-row">
          <ces-button variant="ghost" size="sm" icon="pi pi-download" label="Baixar modelo" [loading]="baixandoModelo()" (pressed)="baixarModelo()"></ces-button>
          <ces-button icon="pi pi-upload" label="Enviar e processar" [loading]="enviando()" [disabled]="!podeEnviar()" (pressed)="enviar()"></ces-button>
        </div>
      </ces-form-section>

      @if (atual(); as imp) {
        <ces-form-section title="Resultado" icon="pi pi-list-check">
          <div class="importacao__cabecalho" aria-live="polite">
            <span class="ces-cell-strong">{{ imp.nomeArquivo }}</span>
            <ces-status [tone]="situacao(imp).tone" [icon]="situacao(imp).icon">{{ situacao(imp).label }}</ces-status>
          </div>
          @if (imp.status === 'FALHOU') {
            <ces-callout tone="danger" title="A planilha não foi processada">{{ imp.mensagem }}</ces-callout>
          } @else {
            <div class="ces-kpis importacao__resumo">
              <div class="ces-kpi"><span class="ces-kpi__body"><span class="ces-kpi__label">Linhas lidas</span><span class="ces-kpi__value">{{ imp.totalLinhas }}</span></span></div>
              <div class="ces-kpi" data-tone="success"><span class="ces-kpi__body"><span class="ces-kpi__label">{{ rotuloInseridos() }}</span><span class="ces-kpi__value">{{ imp.inseridos }}</span></span></div>
              <div class="ces-kpi" data-tone="info"><span class="ces-kpi__body"><span class="ces-kpi__label">Atualizados</span><span class="ces-kpi__value">{{ imp.atualizados }}</span></span></div>
              <div class="ces-kpi" [attr.data-tone]="imp.erros ? 'warning' : 'neutral'"><span class="ces-kpi__body"><span class="ces-kpi__label">Com erro</span><span class="ces-kpi__value">{{ imp.erros }}</span></span></div>
            </div>
          }
          @if (imp.detalheErros.length) {
            <ces-data-table caption="Linhas com erro" [columns]="colunasErros" [rows]="imp.detalheErros" [rowKey]="chaveErro"
              searchPlaceholder="Linha, referência ou motivo" [pageSizeOptions]="[10, 25, 50]"></ces-data-table>
            @if (imp.erros > imp.detalheErros.length) {
              <p class="ces-cell-muted">Mostrando as primeiras {{ imp.detalheErros.length }} de {{ imp.erros }} linhas com erro.</p>
            }
          }
        </ces-form-section>
      }

      <ces-form-section title="Histórico" icon="pi pi-history">
        <ces-data-table caption="Importações anteriores" [searchable]="false" [columns]="colunasHistorico" [rows]="historico()"
          [state]="estadoHistorico()" [stateTitle]="erroHistorico() ? 'Não foi possível carregar o histórico' : 'Nenhuma importação feita'" [stateDescription]="erroHistorico() ?? 'As importações aparecem aqui assim que forem enviadas.'"
          rowActionLabel="Ver resultado" rowActionIcon="pi pi-eye" [rowLabel]="rotuloHistorico" (rowActivate)="selecionar($event)">
          <ng-template cesCell="status" let-row>
            <ces-status [tone]="situacao(row).tone" [icon]="situacao(row).icon">{{ situacao(row).label }}</ces-status>
          </ng-template>
        </ces-data-table>
      </ces-form-section>

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Fechar" (pressed)="visible.set(false)"></ces-button>
      </div>
    </ces-form-drawer>
  `
})
export class ImportacaoObrasDrawerComponent {
  private readonly servico = inject(ObrasKitService);
  private readonly notificacoes = inject(NotificationService);

  readonly visible = model(false);
  readonly tipo = input.required<TipoImportacaoObra>();
  readonly titulo = input('Importar planilha');
  readonly descricao = input<string | undefined>();
  /** Opções de contrato; quando a importação exige contrato, o select aparece. */
  readonly contratos = input<readonly CesSelectOption<string>[]>([]);
  readonly exigeContrato = input(false);
  /** Contrato pré-selecionado (ex.: o filtro atual da listagem). */
  readonly contratoInicial = input<string | null | undefined>(null);
  readonly rotuloInseridos = input('Inseridos');

  /** Emite quando uma importação termina (com ou sem erros por linha), para a tela recarregar. */
  readonly concluida = output<HistoricoImportacaoObraResponse>();

  protected readonly limiteBytes = 20 * 1024 * 1024;
  protected readonly idContrato = signal<string | null>(null);
  protected readonly arquivo = signal<File | null>(null);
  protected readonly enviando = signal(false);
  protected readonly baixandoModelo = signal(false);
  protected readonly tentouEnviar = signal(false);
  protected readonly atual = signal<HistoricoImportacaoObraResponse | null>(null);
  protected readonly historico = signal<HistoricoImportacaoObraResponse[]>([]);
  protected readonly carregandoHistorico = signal(false);
  protected readonly erroHistorico = signal<string | null>(null);

  protected readonly estadoHistorico = computed(() =>
    this.carregandoHistorico() ? 'loading' : this.erroHistorico() ? 'error' : this.historico().length ? 'ready' : 'empty'
  );
  protected readonly erroContrato = computed(() =>
    this.tentouEnviar() && this.exigeContrato() && !this.idContrato() ? 'Selecione o contrato.' : undefined
  );
  protected readonly podeEnviar = computed(() => !!this.arquivo() && !this.enviando() && this.atual()?.status !== 'PROCESSANDO');

  protected readonly colunasErros: CesDataTableColumn<ErroLinhaImportacao>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '5rem', priority: 1 },
    { key: 'referencia', header: 'Referência', width: '10rem' },
    { key: 'motivo', header: 'Motivo', priority: 1 }
  ];
  protected readonly chaveErro = (e: ErroLinhaImportacao) => `${e.linha}-${e.referencia ?? ''}-${e.motivo}`;

  protected readonly colunasHistorico: CesDataTableColumn<HistoricoImportacaoObraResponse>[] = [
    { key: 'dataCriacao', header: 'Enviada em', priority: 1, width: '10rem', value: (h) => h.dataCriacao, display: (h) => formatarDataHora(h.dataCriacao) },
    { key: 'nomeArquivo', header: 'Arquivo', priority: 1 },
    { key: 'usuarioCriacao', header: 'Usuário' },
    { key: 'status', header: 'Situação', type: 'custom', priority: 1, value: (h) => h.status, display: (h) => situacaoImportacao(h.status, h.erros).label },
    { key: 'erros', header: 'Erros', type: 'integer', width: '5rem' }
  ];
  protected readonly rotuloHistorico = (h: HistoricoImportacaoObraResponse) => h.nomeArquivo;

  private acompanhamento?: Subscription;

  constructor() {
    effect(() => {
      if (this.visible()) {
        this.idContrato.set(this.contratoInicial() ?? this.idContrato());
        this.carregarHistorico();
      }
    });
    inject(DestroyRef).onDestroy(() => this.acompanhamento?.unsubscribe());
  }

  protected situacao(h: HistoricoImportacaoObraResponse) {
    return situacaoImportacao(h.status, h.erros);
  }

  protected baixarModelo(): void {
    this.baixandoModelo.set(true);
    this.servico.baixarModelo(this.tipo()).subscribe({
      next: (blob) => {
        this.baixandoModelo.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = NOME_MODELO[this.tipo()];
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => {
        this.baixandoModelo.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected enviar(): void {
    this.tentouEnviar.set(true);
    const arquivo = this.arquivo();
    const contrato = this.idContrato();
    if (!arquivo || (this.exigeContrato() && !contrato)) return;
    const envio$ =
      this.tipo() === 'KITS'
        ? this.servico.importarKits(contrato!, arquivo)
        : this.tipo() === 'CARTEIRA'
          ? this.servico.importarCarteira(contrato!, arquivo)
          : this.servico.importarOrcamento(arquivo);
    this.enviando.set(true);
    envio$.subscribe({
      next: (importacao) => {
        this.enviando.set(false);
        this.tentouEnviar.set(false);
        this.arquivo.set(null);
        this.notificacoes.info('Planilha recebida. O processamento continua mesmo se você fechar esta janela.');
        this.mostrar(importacao);
      },
      error: (e) => {
        this.enviando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected selecionar(importacao: HistoricoImportacaoObraResponse): void {
    this.servico.buscarImportacao(importacao.id).subscribe({
      next: (completa) => this.mostrar(completa),
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected aoFechar(): void {
    // O servidor continua processando; ao reabrir, o histórico mostra o andamento.
    this.acompanhamento?.unsubscribe();
  }

  private mostrar(importacao: HistoricoImportacaoObraResponse): void {
    this.atual.set(importacao);
    this.acompanhamento?.unsubscribe();
    if (importacao.status !== 'PROCESSANDO') return;
    this.acompanhamento = timer(INTERVALO_ACOMPANHAMENTO_MS, INTERVALO_ACOMPANHAMENTO_MS)
      .pipe(
        switchMap(() => this.servico.buscarImportacao(importacao.id)),
        filter((r) => r.status !== 'PROCESSANDO'),
        take(1)
      )
      .subscribe({
        next: (final) => {
          this.atual.set(final);
          this.carregarHistorico();
          this.concluida.emit(final);
          if (final.status === 'FALHOU') {
            this.notificacoes.error('A importação falhou. Veja o motivo no resultado.');
          } else if (final.erros > 0) {
            this.notificacoes.warning(`Importação concluída com ${final.erros} ${final.erros === 1 ? 'linha' : 'linhas'} com erro.`);
          } else {
            this.notificacoes.success('Importação concluída.');
          }
        },
        error: (e) => this.notificacoes.error(getErrorMessage(e))
      });
  }

  private carregarHistorico(): void {
    this.carregandoHistorico.set(true);
    this.erroHistorico.set(null);
    this.servico.listarImportacoes(this.tipo()).subscribe({
      next: (pagina) => {
        this.carregandoHistorico.set(false);
        this.historico.set(pagina.content);
      },
      error: (e) => {
        this.carregandoHistorico.set(false);
        this.erroHistorico.set(getErrorMessage(e));
      }
    });
  }
}
