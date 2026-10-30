---
nome: dashboard-operacional
titulo: Dashboard operacional
descricao: Monta dashboards com KPIs, tendência, rankings e tabela de exceções para acompanhamento de uma área (almoxarifado, obras, SST, chamados, financeiro).
autoridades: []
---
# Dashboard operacional

## Composição padrão
1. **Linha de KPIs** (3 a 5): volume do período, variação vs. período anterior, backlog/pendências, indicador de prazo (SLA, atraso).
2. **Tendência**: gráfico de linha ou área por dia/semana/mês (`granularidade`).
3. **Ranking**: barras horizontais com os 10 maiores (material, obra, equipe, fornecedor, tipo de desvio).
4. **Composição**: pizza ou rosca somente com até 6 categorias; acima disso use barras.
5. **Exceções**: tabela com os itens que exigem ação (atrasados, divergentes, acima do limite), ordenada por criticidade.

## Como montar
1. Obtenha um resultado agregado por componente (ex.: r1 por dia, r2 por material, r3 com as exceções).
2. Use `obter_indicadores` para KPIs oficiais disponíveis antes de calcular manualmente.
3. Chame `criar_artefato` com `tipo: dashboard` e `largura: metade` para gráficos lado a lado.
4. Explique em 3 a 5 bullets o que o dashboard revela.

## Sugestões por área
- **Almoxarifado**: saídas por dia, top materiais, reservas vencendo, solicitações pendentes por status.
- **Obras**: obras por status, obras atrasadas, materiais pendentes por obra.
- **SST**: desvios por tipo e gravidade, inspeções realizadas vs. meta, eventos por mês.
- **Chamados**: abertos x resolvidos, tempo médio de resolução, fila por prioridade.
- **Financeiro**: pagamentos por status, valores por fornecedor, compras aguardando aprovação.
