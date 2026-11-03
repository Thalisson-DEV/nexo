import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFormSectionComponent,
  CesPageHeaderComponent,
  CesStatusComponent,
  CesTabPanelDirective,
  CesTabsComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import {
  Fechamento,
  FechamentoObra,
  LinhaKitFechamento,
  LinhaMaterialFechamento,
  LinhaServicoFechamento,
  MedicaoResumo
} from '../../../core/models/obras-medicoes.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ObrasMedicoesService } from '../../../core/services/obras-medicoes.service';
import { AjudaListagemComponent } from '../../../shared/listagem/ajuda-listagem.component';
import { formatarMoeda } from '../obras-ui';
import { SITUACAO_EXECUCAO, STATUS_MEDICAO, TIPO_MEDICAO, contarCodigos, rotuloCompetencia } from './medicoes.logic';

const LIMITE = 200;
const OPERACAO = { INSTALAR: 'Instalar', RETIRAR: 'Retirar' } as const;

/**
 * Fechamento de uma ou várias obras: lançado × concluído de serviços e materiais, kits e medições. Aceita a coluna de
 * códigos colada do Excel; o resumo vem primeiro e o detalhe abre ao escolher a obra.
 */
@Component({
  selector: 'app-fechamento-obra',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesFormSectionComponent,
    CesPageHeaderComponent,
    CesStatusComponent,
    CesTabPanelDirective,
    CesTabsComponent,
    CesTextareaComponent,
    AjudaListagemComponent
  ],
  template: `
    <div class="ces-page">
      <ces-page-header title="Fechamento de obra" icon="pi pi-file-check"
        [breadcrumb]="[{ label: 'Obras' }, { label: 'Medições' }, { label: 'Fechamento de obra' }]"
        description="Serviços e materiais lançados × concluídos com o valor executado, kits e medições geradas de uma ou várias obras."
        helpLabel="Ajuda sobre o fechamento" (help)="ajuda.show()">
      </ces-page-header>

      <ces-form-section title="Obras" icon="pi pi-building" description="Cole a coluna de códigos do Excel, um por linha, ou separe por vírgula.">
        <ces-textarea label="Códigos das obras" [labelVisible]="false" [rows]="4" [(value)]="codigos" placeholder="Ex.: 7000001&#10;7100002"
          [description]="quantidade() ? quantidade() + (quantidade() === 1 ? ' código' : ' códigos') + (quantidade() > limite ? ' — o limite é ' + limite + ' por vez' : '') : undefined"
          [error]="quantidade() > limite ? 'Gere o fechamento de até ' + limite + ' obras por vez.' : undefined"></ces-textarea>
        <div class="ces-actions-row fechamento__acoes">
          <ces-button variant="outline" icon="pi pi-file-excel" label="Exportar XLSX" [loading]="exportando()"
            [disabled]="!quantidade() || quantidade() > limite" (pressed)="exportar()"></ces-button>
          <ces-button icon="pi pi-search" label="Gerar fechamento" [loading]="gerando()" [disabled]="!quantidade() || quantidade() > limite" (pressed)="gerar()"></ces-button>
        </div>
      </ces-form-section>

      @if (erro(); as mensagem) {
        <ces-async-state state="error" title="Não foi possível gerar o fechamento" [description]="mensagem"></ces-async-state>
      } @else if (resultado(); as r) {
        @if (r.naoEncontrados.length) {
          <ces-callout tone="warning" [dense]="true" [title]="r.naoEncontrados.length === 1 ? '1 código sem obra' : r.naoEncontrados.length + ' códigos sem obra'">
            {{ r.naoEncontrados.join(', ') }}
          </ces-callout>
        }
        @if (r.obras.length) {
          <ces-data-table title="Resumo" exportFileName="fechamento-resumo" searchPlaceholder="Obra, título ou município"
            rowActionLabel="Ver detalhe" rowActionIcon="pi pi-angle-down" [columns]="colunasResumo" [rows]="r.obras" [rowKey]="chaveObra"
            [rowLabel]="rotuloObra" (rowActivate)="ver($event)">
            <ng-template cesCell="obra" let-row>
              <span class="ces-cell-strong">{{ row.numero }}</span>
              <span class="ces-cell-muted">{{ row.titulo }}</span>
            </ng-template>
            <ng-template cesCell="situacao" let-row>
              <ces-status [tone]="situacaoDe(row).tom" [icon]="situacaoDe(row).icone">{{ situacaoDe(row).rotulo }}</ces-status>
            </ng-template>
            <ng-template cesCell="kits" let-row>{{ row.kitsConcluidos }} de {{ row.kitsLancados }}</ng-template>
          </ces-data-table>
        }

        @if (obra(); as o) {
          <section id="fechamento-obra" tabindex="-1" class="fechamento__obra" [attr.aria-label]="'Fechamento da obra ' + o.numero">
            <header class="fechamento__titulo">
              <h2>{{ o.numero }} <span>{{ o.titulo }}</span></h2>
              <span class="fechamento__links">
                <button type="button" (click)="ir(['/obras', o.idObra])"><i class="pi pi-building" aria-hidden="true"></i> Ficha</button>
                <button type="button" (click)="ir(['/obras', o.idObra, 'planejamento'])"><i class="pi pi-map" aria-hidden="true"></i> Planejamento</button>
              </span>
            </header>
            <div class="ces-kpis">
              <div class="ces-kpi" [attr.data-tone]="situacao[o.situacao].tom">
                <span class="ces-kpi__icon"><i class="pi pi-box" aria-hidden="true"></i></span>
                <span class="ces-kpi__body"><span class="ces-kpi__label">Kits concluídos</span>
                  <span class="ces-kpi__value">{{ o.kitsConcluidos }} de {{ o.kitsLancados }}</span>
                  <span class="ces-kpi__detail">{{ situacao[o.situacao].rotulo }}</span></span>
              </div>
              <div class="ces-kpi" data-tone="neutral">
                <span class="ces-kpi__icon"><i class="pi pi-map" aria-hidden="true"></i></span>
                <span class="ces-kpi__body"><span class="ces-kpi__label">Planejado</span>
                  <span class="ces-kpi__value">{{ moeda(o.valorPlanejado) }}</span>
                  <span class="ces-kpi__detail">Serviços pelo valor de hoje</span></span>
              </div>
              <div class="ces-kpi" data-tone="info">
                <span class="ces-kpi__icon"><i class="pi pi-check-square" aria-hidden="true"></i></span>
                <span class="ces-kpi__body"><span class="ces-kpi__label">Executado</span>
                  <span class="ces-kpi__value">{{ moeda(o.valorExecutado) }}</span>
                  <span class="ces-kpi__detail">Pelo valor congelado</span></span>
              </div>
              <div class="ces-kpi" [attr.data-tone]="o.valorMedido ? 'success' : 'neutral'">
                <span class="ces-kpi__icon"><i class="pi pi-calculator" aria-hidden="true"></i></span>
                <span class="ces-kpi__body"><span class="ces-kpi__label">Já medido</span>
                  <span class="ces-kpi__value">{{ moeda(o.valorMedido) }}</span>
                  <span class="ces-kpi__detail">{{ o.medicoesAtivas }} {{ o.medicoesAtivas === 1 ? 'medição ativa' : 'medições ativas' }}</span></span>
              </div>
            </div>

            <ces-tabs [(value)]="abaObra" [tabs]="abas()" [ariaLabel]="'Detalhe da obra ' + o.numero">
              <ng-template cesTabPanel="servicos">
                <ces-data-table caption="Serviços" [columns]="colunasServicos" [rows]="o.servicos" [rowKey]="chaveLinha" [exportFileName]="'fechamento-' + o.numero + '-servicos'"
                  [pageSizeOptions]="[25, 50, 100]" [pageSize]="25" stateTitle="Nenhum serviço planejado"></ces-data-table>
              </ng-template>
              <ng-template cesTabPanel="materiais">
                <ces-data-table caption="Materiais" [columns]="colunasMateriais" [rows]="o.materiais" [rowKey]="chaveLinha" [exportFileName]="'fechamento-' + o.numero + '-materiais'"
                  [pageSizeOptions]="[25, 50, 100]" [pageSize]="25" stateTitle="Nenhum material planejado"></ces-data-table>
              </ng-template>
              <ng-template cesTabPanel="kits">
                <ces-data-table caption="Kits lançados × concluídos" [columns]="colunasKits" [rows]="o.kits" [rowKey]="chaveKit" [exportFileName]="'fechamento-' + o.numero + '-kits'"
                  [pageSizeOptions]="[25, 50, 100]" [pageSize]="25" stateTitle="Nenhum kit aplicado">
                  <ng-template cesCell="kit" let-row>
                    <span class="ces-cell-strong">{{ row.codigoKit }}</span>
                    <span class="ces-cell-muted">{{ row.descricao }}</span>
                  </ng-template>
                  <ng-template cesCell="concluidos" let-row>
                    <ces-status [tone]="row.concluidos === row.lancados ? 'success' : row.concluidos ? 'info' : 'neutral'"
                      [icon]="row.concluidos === row.lancados ? 'pi-check-circle' : 'pi-circle'">{{ row.concluidos }} de {{ row.lancados }}</ces-status>
                  </ng-template>
                </ces-data-table>
              </ng-template>
              <ng-template cesTabPanel="medicoes">
                @if (o.medicoes.length) {
                  <ces-data-table caption="Medições" [columns]="colunasMedicoes" [rows]="o.medicoes" [rowKey]="chaveMedicao" [exportFileName]="'fechamento-' + o.numero + '-medicoes'">
                    <ng-template cesCell="status" let-row>
                      <ces-status [tone]="statusDe(row).tom" [icon]="statusDe(row).icone">{{ statusDe(row).rotulo }}</ces-status>
                    </ng-template>
                    <ng-template cesCell="gerada" let-row>{{ row.dataCriacao | date: 'dd/MM/yyyy' }} · {{ row.usuarioCriacao }}</ng-template>
                  </ces-data-table>
                } @else {
                  <ces-callout tone="info" [dense]="true" title="Nenhuma medição gerada">Esta obra ainda não teve medição.</ces-callout>
                }
              </ng-template>
            </ces-tabs>
          </section>
        }
      } @else if (gerando()) {
        <ces-async-state state="loading" title="Gerando o fechamento" [compact]="true"></ces-async-state>
      }
    </div>

    <app-ajuda-listagem #ajuda titulo="Fechamento de obra" entidadePlural="obras" busca="obra, título ou município" [podeCriar]="false">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-copy"></i> Vários códigos de uma vez</h4>
        <p class="help-text-desc">Cole a coluna inteira do Excel: cada linha vira um código. Repetidos contam uma vez, e os códigos sem obra aparecem num aviso sem impedir as demais. O limite é {{ limite }} obras por vez.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-wallet"></i> Valores</h4>
        <p class="help-text-desc">O executado e o medido usam o valor congelado na programação. O planejado usa o valor vigente hoje dos serviços planejados, só para comparar. Kit executado antes do sistema conta como concluído.</p>
      </div>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-file-excel"></i> Planilha</h4>
        <p class="help-text-desc">"Exportar XLSX" gera abas de resumo, serviços, materiais, kits e medições, com a obra em cada linha para filtrar.</p>
      </div>
    </app-ajuda-listagem>
  `,
  styles: `
    .fechamento__acoes { justify-content: flex-end; margin-top: 0.5rem; }
    .fechamento__obra { margin-top: 1.25rem; display: grid; gap: 1rem; scroll-margin-top: 4rem; }
    .fechamento__obra:focus { outline: none; }
    .fechamento__titulo { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 0.5rem; }
    .fechamento__titulo h2 { margin: 0; font-size: var(--ces-font-title, 1.125rem); }
    .fechamento__titulo h2 span { color: var(--ces-foreground-muted); font-weight: 400; }
    .fechamento__links { display: inline-flex; gap: 0.75rem; }
    .fechamento__links button { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0; border: 0; background: transparent; color: var(--ces-brand); font: inherit; cursor: pointer; }
    .fechamento__links button:focus-visible { outline: 2px solid var(--ces-focus-ring, currentColor); outline-offset: 2px; }
  `
})
export class FechamentoObraComponent {
  private readonly api = inject(ObrasMedicoesService);
  private readonly router = inject(Router);
  private readonly notificacoes = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly limite = LIMITE;
  protected readonly moeda = formatarMoeda;
  protected readonly situacao = SITUACAO_EXECUCAO;
  protected readonly statusMedicao = STATUS_MEDICAO;

  protected readonly codigos = signal('');
  protected readonly quantidade = computed(() => contarCodigos(this.codigos()));
  protected readonly resultado = signal<Fechamento | null>(null);
  protected readonly erro = signal<string | null>(null);
  protected readonly gerando = signal(false);
  protected readonly exportando = signal(false);
  protected readonly escolhida = signal<string | null>(null);
  protected readonly abaObra = signal<string | number | undefined>('servicos');

  protected readonly obra = computed(() => {
    const obras = this.resultado()?.obras ?? [];
    return obras.find((o) => o.idObra === this.escolhida()) ?? (obras.length === 1 ? obras[0] : null);
  });
  protected readonly abas = computed(() => {
    const o = this.obra();
    return [
      { value: 'servicos', label: 'Serviços', icon: 'pi pi-wrench', badge: o?.servicos.length ?? 0 },
      { value: 'materiais', label: 'Materiais', icon: 'pi pi-box', badge: o?.materiais.length ?? 0 },
      { value: 'kits', label: 'Kits', icon: 'pi pi-th-large', badge: o?.kits.length ?? 0 },
      { value: 'medicoes', label: 'Medições', icon: 'pi pi-calculator', badge: o?.medicoes.length ?? 0 }
    ];
  });

  protected readonly colunasResumo: CesDataTableColumn<FechamentoObra>[] = [
    { key: 'obra', header: 'Obra', type: 'custom', priority: 1, value: (o) => o.numero, display: (o) => `${o.numero} ${o.titulo}` },
    { key: 'municipio', header: 'Município', value: (o) => o.municipio },
    { key: 'situacao', header: 'Execução', type: 'custom', priority: 1, value: (o) => o.situacao, display: (o) => SITUACAO_EXECUCAO[o.situacao].rotulo },
    { key: 'kits', header: 'Kits concluídos', type: 'custom', value: (o) => o.kitsConcluidos, display: (o) => `${o.kitsConcluidos} de ${o.kitsLancados}` },
    { key: 'valorPlanejado', header: 'Planejado', type: 'currency', value: (o) => o.valorPlanejado },
    { key: 'valorExecutado', header: 'Executado', type: 'currency', priority: 1, value: (o) => o.valorExecutado },
    { key: 'valorMedido', header: 'Medido', type: 'currency', value: (o) => o.valorMedido },
    { key: 'medicoesAtivas', header: 'Medições', type: 'integer', value: (o) => o.medicoesAtivas }
  ];
  protected readonly colunasServicos: CesDataTableColumn<LinhaServicoFechamento>[] = [
    { key: 'codigo', header: 'Código', value: (s) => s.codigo },
    { key: 'descricao', header: 'Serviço', priority: 1, value: (s) => s.descricao },
    { key: 'operacao', header: 'Operação', value: (s) => OPERACAO[s.operacao] },
    { key: 'lancado', header: 'Lançado', type: 'number', value: (s) => s.lancado },
    { key: 'executado', header: 'Executado', type: 'number', priority: 1, value: (s) => s.executado },
    { key: 'unidade', header: 'Un.', value: (s) => s.unidade },
    { key: 'valorExecutado', header: 'Valor executado', type: 'currency', value: (s) => s.valorExecutado }
  ];
  protected readonly colunasMateriais: CesDataTableColumn<LinhaMaterialFechamento>[] = [
    { key: 'codigo', header: 'Código', value: (m) => m.codigo },
    { key: 'descricao', header: 'Material', priority: 1, value: (m) => m.descricao },
    { key: 'operacao', header: 'Operação', value: (m) => OPERACAO[m.operacao] },
    { key: 'lancado', header: 'Lançado', type: 'number', value: (m) => m.lancado },
    { key: 'aplicado', header: 'Aplicado/retirado', type: 'number', priority: 1, value: (m) => m.aplicado },
    { key: 'unidade', header: 'Un.', value: (m) => m.unidade }
  ];
  protected readonly colunasKits: CesDataTableColumn<LinhaKitFechamento>[] = [
    { key: 'kit', header: 'Kit', type: 'custom', priority: 1, value: (k) => k.codigoKit, display: (k) => `${k.codigoKit ?? ''} ${k.descricao ?? ''}` },
    { key: 'concluidos', header: 'Concluídos', type: 'custom', priority: 1, value: (k) => k.concluidos, display: (k) => `${k.concluidos} de ${k.lancados}` },
    { key: 'locais', header: 'Locais', value: (k) => k.locais.join(', ') }
  ];
  protected readonly colunasMedicoes: CesDataTableColumn<MedicaoResumo>[] = [
    { key: 'numero', header: 'Medição', value: (m) => m.numero, display: (m) => `#${m.numero}` },
    { key: 'tipo', header: 'Tipo', value: (m) => TIPO_MEDICAO[m.tipo] },
    { key: 'competencia', header: 'Competência', value: (m) => m.competencia, display: (m) => rotuloCompetencia(m.competencia) },
    { key: 'valorTotal', header: 'Valor', type: 'currency', priority: 1, value: (m) => m.valorTotal },
    { key: 'status', header: 'Situação', type: 'custom', value: (m) => m.status, display: (m) => STATUS_MEDICAO[m.status].rotulo },
    { key: 'gerada', header: 'Gerada', type: 'custom', value: (m) => m.dataCriacao, display: (m) => new Date(m.dataCriacao).toLocaleDateString('pt-BR') }
  ];
  protected readonly chaveObra = (o: FechamentoObra) => o.idObra;
  protected readonly rotuloObra = (o: FechamentoObra) => `obra ${o.numero}`;
  protected readonly chaveLinha = (l: { codigo: string | null; operacao: string; unidade: string | null }, i: number) => `${l.codigo}|${l.operacao}|${l.unidade}|${i}`;
  protected readonly chaveKit = (k: LinhaKitFechamento) => k.codigoKit ?? '';
  protected readonly chaveMedicao = (m: MedicaoResumo) => m.id;

  protected gerar(): void {
    this.gerando.set(true);
    this.erro.set(null);
    this.resultado.set(null);
    this.escolhida.set(null);
    this.api.fechamento(this.codigos()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (r) => {
        this.gerando.set(false);
        this.resultado.set(r);
        if (!r.obras.length) this.notificacoes.error('Nenhum dos códigos informados corresponde a uma obra.');
      },
      error: (e) => {
        this.gerando.set(false);
        this.erro.set(getErrorMessage(e));
      }
    });
  }

  protected exportar(): void {
    this.exportando.set(true);
    this.api.exportarFechamento(this.codigos()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (arquivo) => {
        this.exportando.set(false);
        const url = URL.createObjectURL(arquivo);
        const link = document.createElement('a');
        link.href = url;
        link.download = `fechamento-obras-${new Date().toISOString().slice(0, 10)}.xlsx`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => {
        this.exportando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  /** Mostra o detalhe e leva o foco até ele, que fica abaixo do resumo. */
  protected ver(o: FechamentoObra): void {
    this.escolhida.set(o.idObra);
    setTimeout(() => {
      const secao = document.getElementById('fechamento-obra');
      const reduzir = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      secao?.scrollIntoView({ behavior: reduzir ? 'auto' : 'smooth', block: 'start' });
      secao?.focus({ preventScroll: true });
    });
  }

  protected situacaoDe(o: FechamentoObra) {
    return SITUACAO_EXECUCAO[o.situacao];
  }

  protected statusDe(m: MedicaoResumo) {
    return STATUS_MEDICAO[m.status];
  }

  protected ir(rota: string[]): void {
    void this.router.navigate(rota);
  }
}
