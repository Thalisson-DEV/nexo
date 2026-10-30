---
nome: analise-consumo-materiais
titulo: Análise de consumo de materiais
descricao: Consumo por material, obra, base, equipe e período; tendências, picos, comparação entre meses e recomendações de reposição.
autoridades: [estoque:movimentacao:read]
---
# Análise de consumo de materiais

## Objetivo
Explicar quanto, onde e como o material foi consumido, com números rastreáveis e recomendações práticas.

## Passo a passo
1. **Delimite o período.** Sem período informado, use o mês corrente até hoje e diga isso na resposta. Para tendência, use pelo menos 3 meses.
2. **Obtenha as saídas.** Use `consultar_movimentacoes` filtrando os tipos que representam consumo (ENTREGA, APLICACAO, RETIRADA, BAIXA_AVULSA). Trate DEVOLUCAO e ESTORNO como redutores do consumo; TRANSFERENCIA e ENTRADA_AVULSA não são consumo.
   - Para agregações grandes, prefira `consultar_fonte` na fonte `movimentacoes` com `agrupar_por` (material, obra, mes) em vez de trazer linhas.
   - Para cruzamentos (consumo x saldo x reservas) use `consultar_sql` com junções entre `copiloto.movimentacoes`, `copiloto.saldos` e `copiloto.reservas`.
3. **Compare períodos** com `comparar_periodos` (métrica soma de quantidade, dimensão material ou obra). Destaque variações relevantes (>20% ou top 5 absolutas).
4. **Procure anomalias** com `detectar_anomalias` sobre o consumo agregado por dia ou por material.
5. **Cruze com o saldo** (`consultar_estoque`) para apontar materiais com consumo alto e saldo baixo (cobertura em dias = saldo / consumo médio diário).
6. **Visualize**: `criar_artefato` com KPIs (total consumido, materiais distintos, variação vs. período anterior), gráfico de linha por dia/semana e barras dos 10 maiores materiais ou obras.
7. **Conclua** com: principais consumidores, mudanças de comportamento, riscos de ruptura e próximos passos (ex.: revisar reserva, antecipar compra).

## Cuidados
- Unidades diferentes (m, un, kg) não podem ser somadas entre materiais distintos: agrupe por material antes de somar.
- Diga sempre o período, os tipos de movimentação considerados e a fonte (rN).
- Não chame de "desperdício" sem evidência: fale em "consumo acima do padrão".
