---
nome: relatorio-executivo
titulo: Relatório executivo em PDF
descricao: Estrutura, coleta de dados e redação de relatórios gerenciais em PDF com KPIs, gráficos, tabelas, conclusões e procedência.
autoridades: []
---
# Relatório executivo em PDF

## Estrutura recomendada
1. **Resumo executivo** (3 a 6 frases): o que aconteceu, o número mais importante, o principal risco e a recomendação central.
2. **Indicadores**: 3 a 4 KPIs com comparação ao período anterior (`comparar_com`) e `melhor_quando` quando o sentido importa (custo, atraso = menor).
3. **Seções analíticas** (2 a 5): cada uma com um gráfico ou tabela e um texto que interpreta (o que mudou, por quê, impacto).
4. **Conclusões e recomendações**: ações objetivas, responsáveis prováveis e prazo sugerido.
5. A procedência dos dados é gerada automaticamente a partir dos resultados usados.

## Como montar
1. Colete todos os dados primeiro (rN) e confira os números com `analisar_resultado` quando houver dúvida.
2. Prepare agregações específicas para cada gráfico (ex.: `consultar_fonte` com `agrupar_por`), evitando gráficos com centenas de categorias.
3. Chame `gerar_relatorio_pdf` uma única vez, com todas as seções. Os componentes usam o mesmo formato de `criar_artefato`.
4. Na resposta, resuma em poucas linhas o que o relatório contém.

## Boas práticas
- Título específico ("Consumo de materiais — setembro/2026"), período explícito.
- Tabelas com no máximo 30 a 50 linhas no PDF; o detalhamento completo vai em `exportar_planilha`.
- Não repita no texto todos os números da tabela; interprete.
- Dados externos (web) só com fonte citada e identificados como externos.
