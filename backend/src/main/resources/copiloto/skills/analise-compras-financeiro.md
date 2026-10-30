---
nome: analise-compras-financeiro
titulo: Análise de compras e financeiro
descricao: Solicitações de compra, solicitações de pagamento e documentos financeiros; valores por fornecedor, status, aprovação, prazos e concentração.
autoridades: [financeiro:compra:read, financeiro:pagamento:read, financeiro:documento:read]
---
# Análise de compras e financeiro

## Fontes
- `consultar_compras` e `consultar_pagamentos` (presets) ou `consultar_fonte` em `solicitacoes_compra`, `solicitacoes_pagamento`, `documentos_financeiros` e `fornecedores`.
- Confirme o fluxo de aprovação com `buscar_conceito` ("aprovação financeira") antes de interpretar status.

## Passo a passo
1. Período e recorte (centro de custo, base, fornecedor, status).
2. Volume e valor por status: quanto está aguardando aprovação, aprovado, pago, rejeitado.
3. Fornecedores: concentração (top 5 em valor e % do total), novos fornecedores no período.
4. Prazos: tempo entre solicitação e aprovação/pagamento; itens vencidos ou próximos do vencimento.
5. Compare com o período anterior e destaque variações relevantes.
6. Visualize: KPIs (valor total, pendente de aprovação, pago no período), barras por fornecedor, tabela de pendências ordenada por valor ou vencimento.

## Cuidados
- Valores monetários sempre em R$ com duas casas; diga se o valor é bruto, líquido ou com impostos quando a fonte distinguir.
- Não exponha dados bancários de fornecedores ou colaboradores.
- Pagamento aprovado não é pagamento realizado: respeite o status.
