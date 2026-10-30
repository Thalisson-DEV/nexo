---
nome: analise-descontos
titulo: Análise de descontos em folha
descricao: Descontos por motivo, colaborador, equipe e período; ciclo de aprovação, valores e reincidência, com cuidado com dados pessoais.
autoridades: [desconto:read]
---
# Análise de descontos em folha

## Fontes
- `consultar_descontos` (preset) ou `consultar_fonte` nas fontes `descontos`, `descontos_itens` e `motivos_desconto`.
- Métricas oficiais em `metrica_oficial` e o dataset `descontos` em `consultar_dataset`. Prefira os números oficiais quando existirem.
- Use `buscar_conceito` para confirmar o ciclo do desconto (status e etapas) antes de interpretar.

## Passo a passo
1. Delimite o período (competência ou data de criação) e o recorte (base, equipe, motivo).
2. Some valores por motivo e por status; identifique o que está pendente de aprovação ou de ciência.
3. Reincidência: colaboradores com mais de um desconto no período (conte por colaborador, apresente agregado).
4. Compare com o período anterior (`comparar_periodos`).
5. Visualize com KPIs (valor total, quantidade, ticket médio, pendentes) e barras por motivo.

## Cuidados com dados pessoais
- Descontos são dados sensíveis: apresente por padrão agregados (motivo, equipe, base). Liste nomes somente se o usuário pedir e tiver permissão, e apenas o necessário.
- Nunca exponha CPF, salário ou dados bancários.
- Não faça julgamento sobre colaboradores; descreva fatos e padrões.
