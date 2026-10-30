import {
  DashboardDescontosResponse,
  PrioridadeDescontoItem,
  SeveridadePrioridade
} from '../../../../core/models/desconto-dashboard.models';
import {
  DashboardInsight,
  InsightTone
} from '../../../../shared/components/dashboard/dashboard-ui.components';

/** Ícone por tipo de prioridade — o genérico serve a tipos que o backend venha a criar. */
const ICONE_POR_TIPO: Record<string, string> = {
  VALIDACAO_PARADA: 'pi-hourglass',
  BAIXA_PARADA: 'pi-wallet',
  SEM_COMPROVACAO: 'pi-paperclip',
  GLOSA_ELEVADA: 'pi-percentage',
  REINCIDENCIA: 'pi-user-edit'
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
  if (absoluto >= 1_000_000) return `R$ ${(numero / 1_000_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`;
  if (absoluto >= 1_000) return `R$ ${(numero / 1_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil`;
  return formatarMoeda(numero);
}

export function formatarPercentual(valor: number | null | undefined, casas = 1): string {
  return `${(valor ?? 0).toLocaleString('pt-BR', { maximumFractionDigits: casas })}%`;
}

/**
 * Leitura gerencial do dashboard. As prioridades calculadas pelo backend vêm primeiro
 * — elas já sabem o que está travado e há quanto tempo. Depois entram duas leituras
 * que não são pendências e por isso nunca virariam prioridade, mas explicam o número
 * do topo da tela: quanto do pedido virou desconto, e quanto tempo o ciclo leva.
 */
export function construirInsightsDeDescontos(dados: DashboardDescontosResponse): DashboardInsight[] {
  const insights: DashboardInsight[] = dados.prioridades.map(paraInsight);

  const financeiro = dados.financeiro;
  if (dados.resumo.totalDescontos > 0) {
    insights.push({
      title: 'Recuperação do valor solicitado',
      description:
        `${formatarPercentual(financeiro.taxaRecuperacao)} do que foi pedido virou desconto em folha ` +
        `(${formatarMoeda(financeiro.valorBaixado)} de ${formatarMoeda(financeiro.valorSolicitado)}).`,
      action:
        financeiro.glosaValidacao > 0
          ? `${formatarMoeda(financeiro.glosaValidacao)} foram cortados na validação e ` +
            `${formatarMoeda(financeiro.ajusteBaixa)} na baixa.`
          : 'Nenhum corte registrado entre a solicitação e a baixa no período.',
      icon: 'pi-money-bill',
      tone: 'neutral'
    });
  }

  if (dados.ciclo.amostraValidacao > 0) {
    insights.push({
      title: 'Tempo do ciclo',
      description:
        `Metade dos termos é validada em até ${dados.ciclo.medianaDiasAteValidacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dia(s); ` +
        `9 em cada 10, em até ${dados.ciclo.p90DiasAteValidacao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}.`,
      action:
        dados.ciclo.idadeMaximaPendenteDias > 0
          ? `A pendência mais antiga está parada há ${dados.ciclo.idadeMaximaPendenteDias} dia(s).`
          : 'Não há nenhuma pendência em aberto.',
      icon: 'pi-clock',
      tone: dados.ciclo.p90DiasAteValidacao > 30 ? 'warning' : 'neutral'
    });
  }

  if (!insights.length) {
    return [
      {
        title: 'Nada exigindo ação no recorte',
        description: 'Nenhuma pendência travada, sem comprovação ou fora do padrão foi encontrada.',
        action: 'Confira o período e os filtros antes de concluir que não há nada a tratar.',
        icon: 'pi-check-circle',
        tone: 'positive'
      }
    ];
  }

  return insights;
}

function paraInsight(prioridade: PrioridadeDescontoItem): DashboardInsight {
  return {
    title: prioridade.titulo,
    description: prioridade.evidencia,
    action: prioridade.acaoSugerida,
    icon: ICONE_POR_TIPO[prioridade.tipo] ?? 'pi-exclamation-triangle',
    tone: TOM_POR_SEVERIDADE[prioridade.severidade] ?? 'neutral'
  };
}
