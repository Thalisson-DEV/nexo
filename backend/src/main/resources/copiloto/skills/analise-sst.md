---
nome: analise-sst
titulo: Análise de segurança do trabalho (SST)
descricao: Inspeções, desvios, eventos e metas de SST; tendências, equipes e locais críticos, cumprimento de metas e recomendações preventivas.
autoridades: [sst:inspecao:read, sst:dashboard:read, sst:evento:read]
---
# Análise de segurança do trabalho (SST)

## Fontes
- Inspeções: `consultar_fonte` em `inspecoes`, metas em `metas_inspecao`.
- Desvios: `consultar_desvios_sst` ou fonte `desvios` (tipo, gravidade, equipe, status de tratamento).
- Eventos (acidentes, incidentes, quase acidentes): `consultar_eventos_sst` ou fonte `eventos_sst`.
- Métricas oficiais: `metrica_oficial` (média mensal de inspeções, ranking de desvios por equipe) e datasets `sst_inspecoes`, `sst_desvios`, `sst_eventos`.

## Passo a passo
1. Período padrão: últimos 3 meses completos + mês corrente.
2. Volume: inspeções realizadas vs. meta; desvios por inspeção (taxa); eventos por mês.
3. Onde: equipes, bases e municípios com mais desvios; tipos de desvio mais frequentes.
4. Gravidade e tratamento: desvios graves abertos, tempo de tratamento.
5. Tendência: melhora ou piora vs. período anterior (`comparar_periodos`).
6. Recomendações preventivas ligadas aos dados (ex.: reforçar inspeção na equipe X, treinamento sobre o desvio Y). Normas externas (NRs) podem ser consultadas com `pesquisar_web`, sempre citadas.

## Cuidados
- Eventos com vítimas envolvem dados sensíveis: agregue e evite nomes.
- Em inspeções e desvios de SST existem dois processos de negócio distintos: `processo` (ou `processo_inspecao`) refere-se ao processo operacional da atividade inspecionada (ex.: Linha Viva, Construção); `processo_inspetor` refere-se ao processo de negócio do inspetor que realizou a inspeção (ex.: SESMT, Segurança do Trabalho). Nenhum dos dois deve ser confundido com o processo cadastral de cada colaborador inspecionado.
