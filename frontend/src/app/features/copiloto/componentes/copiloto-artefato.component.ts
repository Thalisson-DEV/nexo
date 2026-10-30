import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { CesButtonComponent, CesIconButtonComponent, CesStatusComponent } from '@ces/ui';
import { CesCalloutComponent } from '@ces/ui/feedback';
import { CesChartComponent, CesChartPoint, CesChartSeries } from '@ces/ui/patterns';
import {
  ArquivoAssistente,
  ArtefatoAssistente,
  ComponenteArtefato,
  GraficoArtefato,
  KpiArtefato,
} from '../../../core/models/assistente.models';
import { CopilotoStore } from '../copiloto.store';
import {
  FORMATO_GRAFICO,
  ICONE_ARTEFATO,
  NATUREZA,
  ROTULO_ARTEFATO,
  TIPO_GRAFICO,
  formatarCelula,
  formatarValor,
} from '../copiloto-formatos';
import { CopilotoMarkdownComponent } from './copiloto-markdown.component';

/**
 * Painel de artefatos do workspace (equivalente ao painel de artefatos/"canvas" do Onyx). Renderiza apenas
 * componentes confiáveis do Nexo a partir de especificações já resolvidas pelo backend — nenhum código
 * do modelo é executado no navegador.
 */
@Component({
  selector: 'app-copiloto-artefato',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    CesButtonComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    CesCalloutComponent,
    CesChartComponent,
    CopilotoMarkdownComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './copiloto-artefato.component.html',
  styleUrl: './copiloto-artefato.component.css',
})
export class CopilotoArtefatoComponent {
  private readonly store = inject(CopilotoStore);

  readonly artefato = input.required<ArtefatoAssistente>();
  readonly artefatos = input<readonly ArtefatoAssistente[]>([]);
  readonly fechar = output<void>();
  readonly selecionar = output<string>();

  protected readonly procedenciaAberta = signal(false);
  protected readonly spec = computed(() => this.artefato().especificacao ?? { tipo: '' });
  protected readonly arquivo = computed<ArquivoAssistente | null>(() => this.spec().arquivo ?? null);
  protected readonly indice = computed(() => this.artefatos().findIndex((item) => item.codigo === this.artefato().codigo));

  protected readonly icone = ICONE_ARTEFATO;
  protected readonly rotulo = ROTULO_ARTEFATO;
  protected readonly natureza = NATUREZA;
  protected readonly formatarValor = formatarValor;
  protected readonly formatarCelula = formatarCelula;

  protected anterior(): void {
    const lista = this.artefatos();
    const i = this.indice();
    if (i > 0) this.selecionar.emit(lista[i - 1].codigo);
  }

  protected proximo(): void {
    const lista = this.artefatos();
    const i = this.indice();
    if (i >= 0 && i < lista.length - 1) this.selecionar.emit(lista[i + 1].codigo);
  }

  protected baixar(): void {
    const arquivo = this.arquivo();
    if (arquivo) void this.store.baixar(arquivo);
  }

  protected abrirPdf(): void {
    const arquivo = this.arquivo();
    if (arquivo) void this.store.abrirEmNovaAba(arquivo);
  }

  protected kpis(componentes: readonly ComponenteArtefato[] | undefined): KpiArtefato[] {
    return (componentes ?? []).filter((c): c is KpiArtefato => c.tipo === 'kpi');
  }

  protected demais(componentes: readonly ComponenteArtefato[] | undefined): Exclude<ComponenteArtefato, KpiArtefato>[] {
    return (componentes ?? []).filter((c): c is Exclude<ComponenteArtefato, KpiArtefato> => c.tipo !== 'kpi');
  }

  protected tipoGrafico(grafico: GraficoArtefato) {
    return TIPO_GRAFICO[grafico.grafico] ?? 'bar';
  }

  protected formatoGrafico(grafico: GraficoArtefato) {
    return FORMATO_GRAFICO[grafico.formato] ?? 'number';
  }

  protected series(grafico: GraficoArtefato): CesChartSeries[] {
    return (grafico.series ?? []).map((serie) => ({ name: serie.nome, values: serie.valores }));
  }

  protected pontos(grafico: GraficoArtefato): CesChartPoint[] {
    return (grafico.pontos ?? []).map((ponto) => ({ x: ponto.x, y: ponto.y, label: ponto.rotulo }));
  }

  protected variacao(kpi: KpiArtefato): { texto: string; tom: 'success' | 'danger' | 'neutral'; icone: string } | null {
    const comparacao = kpi.comparacao;
    if (!comparacao) return null;
    const variacao = comparacao.variacao;
    if (variacao === null || variacao === undefined) {
      return { texto: `Referência: ${formatarValor(comparacao.valor, kpi.formato)} ${comparacao.rotulo}`, tom: 'neutral', icone: 'pi-minus' };
    }
    const bom = kpi.melhorQuando === 'menor' ? variacao < 0 : variacao > 0;
    const sinal = variacao > 0 ? '+' : '';
    const texto = `${sinal}${formatarValor(variacao, 'percentual')} ${comparacao.rotulo}`;
    if (variacao === 0) return { texto, tom: 'neutral', icone: 'pi-minus' };
    return {
      texto,
      tom: kpi.melhorQuando ? (bom ? 'success' : 'danger') : 'neutral',
      icone: variacao > 0 ? 'pi-arrow-up' : 'pi-arrow-down',
    };
  }
}
