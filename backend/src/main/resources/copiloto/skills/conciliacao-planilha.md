---
nome: conciliacao-planilha
titulo: Conciliação de planilha com o Nexo
descricao: Compara uma planilha enviada (inventário, medição, extrato, lista de materiais) com os dados do Nexo e aponta divergências linha a linha.
autoridades: []
---
# Conciliação de planilha com o Nexo

## Objetivo
Dizer exatamente o que confere, o que diverge e o que existe só de um lado, com uma planilha de divergências pronta para ação.

## Passo a passo
1. **Leia o arquivo** com `ler_arquivo` (informe a aba quando houver várias). Identifique a coluna-chave (código do material, número da obra, matrícula, número do documento) e as colunas de valor (quantidade, valor).
2. **Entenda o recorte** da planilha: período, estoque/base, obra. Se não estiver claro, deduza pelo conteúdo e declare a premissa.
3. **Busque o lado do Nexo** com a ferramenta de domínio correspondente (`consultar_estoque`, `consultar_movimentacoes`, `consultar_obras`, `consultar_descontos`, `consultar_pagamentos`...) ou `consultar_fonte`, já agregando pela mesma chave e no mesmo recorte.
4. **Concilie** com `comparar_resultados` (chave de cada lado, métricas equivalentes, tolerância adequada: 0 para quantidades inteiras, 0,01 para valores).
5. **Quando a chave não bate diretamente** (códigos com zeros à esquerda, descrições em vez de códigos, várias abas), use `executar_python` com pandas para normalizar chaves e cruzar, gravando o resultado em CSV/XLSX (ele volta como novo resultado rN).
6. **Entregue**: resumo com totais (linhas conferidas, divergentes, só na planilha, só no Nexo), tabela das maiores divergências e `exportar_planilha` com o resultado da conciliação.

## Cuidados
- Nunca altere dados do Nexo: a conciliação é somente leitura.
- Diferencie "divergência de quantidade" de "item inexistente".
- Informe linhas ignoradas da planilha (vazias, totais, cabeçalhos repetidos).
