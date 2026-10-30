import { DashboardChamadosResponse } from '../../../core/models/chamado-dashboard.models';
import { DashboardInsight } from '../../../shared/components/dashboard/dashboard-ui.components';

export function toPlainChamadosLanguage(value: string): string {
  if (!value) return '';
  const translated = value
    .replace(/\bSLA\b/gi, 'prazo contratual')
    .replace(/\bbacklog\b/gi, 'fila de pendências em aberto')
    .replace(/\bda coorte\b/gi, 'do período selecionado')
    .replace(/\bcoorte\b/gi, 'período selecionado')
    .replace(/\bP90\b/gi, 'prazo de 9 em cada 10 chamados')
    .replace(/\bmediana\b/gi, 'tempo de metade dos chamados')
    .replace(/taxa de resolução/gi, 'percentual de chamados resolvidos')
    .replace(/taxa de reabertura/gi, 'percentual de reaberturas pós-solução')
    .replace(/data de referência/gi, 'fim do período selecionado')
    .replace(/\s*\/\s*/g, ' em relação ao ');
  return translated ? translated.charAt(0).toUpperCase() + translated.slice(1) : translated;
}

export function buildChamadosInsights(data: DashboardChamadosResponse): DashboardInsight[] {
  if (!data.prioridades?.length) {
    return [
      {
        title: 'Operação regular',
        description: 'Nenhum gargalo crítico ou estouro de prazo identificado nos chamados do período.',
        action: 'Mantenha o acompanhamento periódico das filas das equipes.',
        icon: 'pi-check-circle',
        tone: 'positive'
      }
    ];
  }

  return data.prioridades.map((priority) => {
    let tone: 'critical' | 'warning' | 'neutral' | 'positive' = 'neutral';
    let icon = 'pi-info-circle';

    switch (priority.severidade) {
      case 'CRITICA':
        tone = 'critical';
        icon = 'pi-bolt';
        break;
      case 'ALTA':
        tone = 'warning';
        icon = 'pi-clock';
        break;
      case 'MEDIA':
        tone = 'warning';
        icon = 'pi-exclamation-circle';
        break;
      case 'NORMAL':
      case 'BAIXA':
      default:
        tone = priority.tipo === 'OPERACAO_REGULAR' ? 'positive' : 'neutral';
        icon = priority.tipo === 'OPERACAO_REGULAR' ? 'pi-check-circle' : 'pi-info-circle';
        break;
    }

    return {
      title: toPlainChamadosLanguage(priority.titulo),
      description: toPlainChamadosLanguage(priority.evidencia),
      action: toPlainChamadosLanguage(priority.acaoSugerida),
      icon,
      tone
    };
  });
}
