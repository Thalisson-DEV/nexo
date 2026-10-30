import { GraficoPainel } from '../../../../core/models/assistente.models';
import { VistaAnalise } from '../../../../shared/components/dashboard/dashboard-analise.models';
import { graficoPainel } from '../../../copiloto/painel';

/**
 * O que a análise aberta acrescenta ao contexto do Copiloto quando o usuário pergunta a partir dela:
 * os gráficos secundários (agregações visíveis) e os indicadores do recorte. É pista, não autorização —
 * o agente aprofunda pelas próprias ferramentas, com as permissões do usuário.
 */
export function graficosDaAnalise(vista: VistaAnalise | null): GraficoPainel[] {
  if (!vista) return [];
  const prefixo = `analise.${vista.chave}`;
  const indicadores = graficoPainel(
    `${prefixo}.indicadores`,
    'tabela',
    `${vista.eyebrow}: ${vista.titulo} — indicadores`,
    vista.kpis.map((k) => `${k.rotulo}: ${k.valor}${k.detalhe ? ` (${k.detalhe})` : ''}`),
    vista.kpis.map(() => null),
    { dimensao: 'indicador' }
  );
  const graficos = vista.graficos.map((g) =>
    graficoPainel(
      `${prefixo}.${g.id}`,
      g.tipo,
      `${vista.titulo} — ${g.titulo}`,
      g.rotulos,
      g.series.map((s) => ({ nome: s.name, valores: s.values })),
      { formato: g.formato === 'currency' ? 'moeda' : g.formato === 'integer' ? 'inteiro' : g.formato === 'percent' ? 'percentual' : 'numero' }
    )
  );
  return [indicadores, ...graficos];
}
