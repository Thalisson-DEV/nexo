## Why

O processo de Obras do Nexo hoje cadastra obras, anexos, subprojetos e status, mas não cobre o ciclo técnico: levantar o projeto
sobre o PDF, aplicar kits construtivos, programar equipes com base em capacidade e meta, acompanhar a execução e medir o que foi
feito. A operação ainda depende de um sistema externo de referência para esse ciclo, e a decisão é trazer essas funcionalidades
para o Nexo, melhorando onde o sistema de referência é frágil (sem offline, sem controle de concorrência, preço não congelado,
Gantt instável, preferências de visualização só no navegador).

## What Changes

Etapa 1 (esta change), em frentes paralelas:

- **Kits construtivos**: catálogo por contrato com itens de serviço (Ações do contrato) e de material (Materiais), ação
  Instalar/Retirar, grupo, classe de tensão; importação em lote e manutenção pela tela. Valoração somente em R$.
- **Carteira de obras**: novos dados na Obra (classe de tensão, prazo regulado, prioridade, lote previsto, valores de projeto,
  material e serviço previstos, deslocamento calculado a partir da base), importação da carteira e dos serviços/orçamento previstos,
  obras próximas e indicadores da carteira.
- **Planejamento sobre o PDF do projeto**: visualizador do projeto com pontos (postes/estruturas) e vãos posicionados na folha,
  kits aplicados com quantidade prevista/executada e status por item, anotações, extração de texto por região, rotação persistida no
  anexo, seleção por clique ou área, árvore de itens, avanço por kit e orçamento importado para comparação.
- **Programação de obras**: agendamento de itens planejados para equipe e período, com projeção de capacidade (jornada, plataforma,
  deslocamento) e meta financeira do período, condições operacionais (linha viva, desligamento, finaliza obra, turno, situação da
  intervenção), histórico de reprogramações e visão Gantt.
- **Turnos técnicos (web)**: os turnos e o dashboard de turnos existentes passam a atender o processo de Obras: vínculo do turno às
  programações, APR, ocorrências, relatório diário, desempenho realizado × meta do dia, titular/substituto, só para análise no web; o lançamento é da equipe,
  pelo app.
- **Documentação técnica da obra (web)**: visita prévia (checklist), As Built por ponto com plantas marcadas e versionadas, book de
  equipamentos (religador/regulador/trafo auxiliar).
- **Medições de obras**: pré-fechamento do levantamento, medição parcial com bloqueio dos itens medidos, medição por ciclo com saldo,
  relatório detalhado orçado × faturado e fechamento de obra.
- **Canteiros de postes e materiais da programação**: estoque de postes por canteiro, e requisição de materiais gerada a partir da
  programação usando o fluxo de solicitação técnica existente.
- **Central de operações de obras**: painel do dia (equipes em turno, frota, alertas, financeiro, meta por tipo de equipe, ritmo e
  projeção, ociosidade, risco de prazo), mapa de obras com rota até a base e camadas de telemetria.
- **Relatórios de obras**: avaliação da carteira, programação, programado × executado por equipe/dia, execução por turno, prazos,
  produtividade por equipe e por colaborador, financeiro.

Fica para a etapa 2 (change `execucao-campo-obras`):

- Execução no app de campo (agenda da equipe, marcar ponto/vão/item como feito offline, GPS da execução).
- Evidências fotográficas por ponto/vão capturadas no app, revisão/avaliação de fotos, inconformidades e ranking de qualidade.
- Visita prévia e As Built lançados pelo supervisor no app; APR e ocorrências lançadas pela equipe no app; obra não programada registrada em campo.
- Status em tempo real da equipe derivado do app + telemetria (trabalhando, em rota, aguardando, na base).
- Georreferenciamento automático dos pontos a partir da execução em campo.

Fora de escopo nas duas etapas: valoração em US (unidade de serviço) e o módulo de homologação/tickets do sistema de referência.

## Capabilities

### New Capabilities
- `kits-construtivos`: catálogo de kits por contrato, itens de serviço e material, importação e manutenção.
- `carteira-obras`: dados de carteira da obra, importações, deslocamento da base, obras próximas e indicadores.
- `planejamento-pdf-obra`: visualizador do projeto em PDF com pontos, vãos, kits aplicados, anotações e ferramentas de leitura.
- `programacao-obras`: agendamento de itens planejados, capacidade, meta, condições operacionais, reprogramações e Gantt.
- `turnos-obras`: extensão dos turnos e do dashboard de turnos ao processo de Obras, só para análise no web.
- `documentacao-tecnica-obra`: visita prévia, book da obra (fotos por ponto), As Built e equipamentos.
- `medicoes-obras`: pré-fechamento, medição parcial, medição por ciclo, relatório detalhado e fechamento de obra.
- `recursos-obras`: canteiros de postes e requisição de materiais a partir da programação.
- `central-operacoes-obras`: painel operacional, mapa de obras e telemetria das equipes.
- `relatorios-obras`: relatórios gerenciais do processo de Obras.

### Modified Capabilities
<!-- openspec/specs/ ainda está vazio; os comportamentos atuais de Obra, Turno e Equipe são preservados e estendidos via ADDED. -->

## Impact

- Backend: módulo `obras` (novas entidades de planejamento, programação, medição, documentação), módulo novo de kits (ou
  subdomínio de `obras`), extensões em `execucao` (turno técnico), `estoque` (canteiro de postes, solicitação a partir da
  programação), `rh` (capacidade e meta da equipe), `frota` (posição para o mapa). Migrações Flyway a partir de V195.
- Frontend: `features/obras` (lista, detalhe, planejamento, programação, medições, documentação), `features/execucao` (turnos e
  dashboard), nova central de operações; nova dependência `pdfjs-dist` registrada no Design System quando gerar componente.
- Permissões novas (`obras:kit:*`, `obras:planejamento:*`, `obras:programacao:*`, `obras:medicao:*`, `obras:documentacao:*`).
- Integrações: importação de planilhas (carteira, kits, serviços/orçamento previstos, orçado × faturado); serviço de rotas para
  deslocamento base → obra.
- Comportamentos existentes de Obra, Turno comercial, Equipe e Solicitação de Material não podem regredir.
