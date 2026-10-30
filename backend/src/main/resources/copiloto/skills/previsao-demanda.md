---
nome: previsao-demanda
titulo: Previsão de demanda e cobertura de estoque
descricao: Projeção de consumo com Python (médias móveis, sazonalidade simples, regressão), cobertura de estoque em dias e ponto de reposição.
autoridades: [estoque:movimentacao:read, estoque:saldo:read]
---
# Previsão de demanda e cobertura de estoque

## Passo a passo
1. Obtenha o histórico diário ou semanal de consumo por material (pelo menos 8 semanas) com `consultar_fonte` agrupando por `data` (ou `mes`) e material; e o saldo atual com `consultar_estoque`.
2. Em `executar_python`, leia `rN.csv` com pandas e:
   - preencha dias sem consumo com zero;
   - calcule média móvel (7/28 dias), desvio padrão e tendência (regressão linear simples com numpy);
   - projete as próximas 4 semanas com intervalo aproximado (média ± 1,65·desvio para ~90%);
   - calcule cobertura = saldo / consumo médio diário e ponto de reposição = consumo médio diário × lead time + estoque de segurança;
   - salve `previsao.csv` (material, consumo_medio_diario, projecao_30d, cobertura_dias, ponto_reposicao) e um gráfico `previsao.png`.
3. O CSV gerado volta como resultado rN: use-o em `criar_artefato` (tabela ordenada por menor cobertura) ou no relatório.

## Cuidados
- Declare o método e as premissas (lead time assumido, janela histórica).
- Poucos dados (< 8 pontos) = estimativa frágil: diga isso claramente.
- Previsão não é compromisso de compra: apresente como apoio à decisão.
