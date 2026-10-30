import {
  DashboardFinanceiroResponse,
  PrioridadeFinanceiraItem,
  SeveridadePrioridade
} from '../../../core/models/financeiro-dashboard.models';
import {
  DashboardInsight,
  InsightTone
} from '../../../shared/components/dashboard/dashboard-ui.components';

/** Ícone por tipo de prioridade — o genérico atende tipos que o backend venha a criar. */
const ICONE_POR_TIPO: Record<string, string> = {
  APROVACAO_PARADA: 'pi-hourglass',
  RASCUNHO_ESQUECIDO: 'pi-file-edit',
  VENCIMENTO_PROXIMO: 'pi-calendar-clock',
  VENCIDO_SEM_COMPROVANTE: 'pi-exclamation-triangle',
  DOCUMENTO_AGUARDANDO_CONFERENCIA: 'pi-inbox',
  SEM_NOTA_FISCAL: 'pi-receipt',
  CORTE_ELEVADO: 'pi-percentage',
  FORNECEDOR_NAO_HOMOLOGADO: 'pi-building',
  CONCENTRACAO_FORNECEDOR: 'pi-chart-pie',
  DOCUMENTO_DUPLICADO: 'pi-clone'
};

const TOM_POR_SEVERIDADE: Record<SeveridadePrioridade, InsightTone> = {
  CRITICA: 'critical',
  ALTA: 'warning',
  MEDIA: 'neutral',
  BAIXA: 'neutral'
};

export function formatarMoeda(valor: number | null | undefined): string {
  return (valor ?? 0).toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: 2
  });
}

/**
 * Valor abreviado para eixo de gráfico e cartão apertado: `R$ 1,2 mil`, `R$ 3,4 mi`.
 * Um eixo com sete dígitos por marca fica ilegível muito antes de ficar impreciso.
 */
export function formatarMoedaCompacta(valor: number | null | undefined): string {
  const numero = valor ?? 0;
  const absoluto = Math.abs(numero);
  if (absoluto >= 1_000_000) {
    return `R$ ${(numero / 1_000_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`;
  }
  if (absoluto >= 1_000) {
    return `R$ ${(numero / 1_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil`;
  }
  return formatarMoeda(numero);
}

export function formatarPercentual(valor: number | null | undefined, casas = 1): string {
  return `${(valor ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: casas })}%`;
}

/**
 * Leitura gerencial do dashboard. As prioridades calculadas pelo backend vêm
 * primeiro — elas já sabem o que está travado e há quanto tempo. Depois entram
 * três leituras que não são pendências e por isso nunca virariam prioridade, mas
 * explicam os números do topo da tela: quanto do pedido foi autorizado, quanto
 * tempo a decisão leva e quanto da despesa tem documento fiscal.
 */
export function construirInsightsFinanceiros(dados: DashboardFinanceiroResponse): DashboardInsight[] {
  const insights: DashboardInsight[] = dados.prioridades.map(paraInsight);
  const { resumo, valores, ciclo, conformidade } = dados;

  if (resumo.total > 0) {
    insights.push({
      title: 'Quanto do que foi pedido saiu autorizado',
      description:
        `${formatarPercentual(valores.taxaAutorizacao)} do valor pedido foi aprovado ` +
        `(${formatarMoeda(valores.valorAprovado)} de ${formatarMoeda(valores.valorSolicitado)}).`,
      action:
        valores.corteNaAprovacao > 0
          ? `${formatarMoeda(valores.corteNaAprovacao)} foram cortados na hora de aprovar, ` +
            `e ${formatarMoeda(valores.valorRejeitado)} foram recusados por inteiro.`
          : 'Nenhum corte na aprovação no período: o que foi pedido saiu como foi pedido.',
      icon: 'pi-money-bill',
      tone: 'neutral'
    });
  }

  if (ciclo.amostraDecisao > 0) {
    insights.push({
      title: 'Tempo até a decisão',
      description:
        `Metade dos pedidos é decidida em até ${umaCasa(ciclo.medianaDiasAteDecisao)} dia(s); ` +
        `9 em cada 10, em até ${umaCasa(ciclo.p90DiasAteDecisao)}.`,
      action:
        ciclo.idadeMaximaFilaDias > 0
          ? `O pedido mais antigo está esperando decisão há ${ciclo.idadeMaximaFilaDias} dia(s).`
          : 'Não há nenhum pedido esperando decisão.',
      icon: 'pi-clock',
      tone: ciclo.p90DiasAteDecisao > 15 ? 'warning' : 'neutral'
    });
  }

  if (ciclo.decisoesDesfeitas > 0) {
    insights.push({
      title: 'Decisões desfeitas',
      description:
        `${ciclo.decisoesDesfeitas} ${ciclo.decisoesDesfeitas === 1 ? 'decisão foi desfeita' : 'decisões foram desfeitas'} ` +
        'nos pedidos do período e voltaram para a fila de aprovação.',
      action: 'Veja o motivo na linha do tempo de cada solicitação; motivos repetidos apontam falha no pedido ou na análise.',
      icon: 'pi-undo',
      tone: 'warning'
    });
  }

  if (resumo.aprovadas > 0) {
    const cobertura = conformidade.coberturaFiscal;
    insights.push({
      title: 'Despesa com documento fiscal',
      description:
        `${formatarPercentual(cobertura)} das solicitações aprovadas no período têm nota, ` +
        `boleto ou recibo guardado no sistema.`,
      action:
        conformidade.aprovadasSemDocumentoFiscal > 0
          ? `Faltam documentos em ${conformidade.aprovadasSemDocumentoFiscal} despesa(s), ` +
            `somando ${formatarMoeda(conformidade.valorSemDocumentoFiscal)}.`
          : 'Toda despesa aprovada no período já tem documento fiscal guardado.',
      icon: 'pi-receipt',
      tone: cobertura >= 90 ? 'positive' : cobertura >= 70 ? 'neutral' : 'warning'
    });
  }

  if (!insights.length) {
    return [
      {
        title: 'Nada exigindo ação no recorte',
        description:
          'Nenhum pedido travado, vencimento estourado ou documento faltando foi encontrado.',
        action: 'Confira o período e os filtros antes de concluir que não há nada a tratar.',
        icon: 'pi-check-circle',
        tone: 'positive'
      }
    ];
  }

  return insights;
}

function paraInsight(prioridade: PrioridadeFinanceiraItem): DashboardInsight {
  return {
    title: prioridade.titulo,
    description: prioridade.evidencia,
    action: prioridade.acaoSugerida,
    icon: ICONE_POR_TIPO[prioridade.tipo] ?? 'pi-exclamation-triangle',
    tone: TOM_POR_SEVERIDADE[prioridade.severidade] ?? 'neutral'
  };
}

function umaCasa(valor: number): string {
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 });
}
