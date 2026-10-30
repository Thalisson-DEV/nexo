## Why

A etapa 1 (`evoluir-processo-obras`) leva planejamento, programação, apontamento web e medições para o Nexo, mas a execução
continua sendo informada pelo backoffice. Para o ciclo ficar completo e confiável, a equipe de campo precisa registrar no app,
mesmo sem sinal, o que executou em cada ponto e vão, com evidência fotográfica e localização. Esta change fica para depois da
etapa 1 e depende dela.

## What Changes

- Agenda da equipe no app: programações do dia/período com obra, itens, condições operacionais e rota até a obra.
- Projeto da obra no app (PDF com pontos e vãos) para consulta e execução, disponível offline.
- Marcar ponto, vão ou item como feito (total ou parcial) offline, gerando o mesmo apontamento de execução da etapa 1, com
  GPS e horário, sincronizado com o vocabulário normativo (salvo no dispositivo, enfileirado, sincronizando, sincronizado, conflito).
- Evidências fotográficas obrigatórias/configuráveis por ponto e vão ao marcar como feito, com anexação posterior pelo web.
- Turno de Obras no app: abertura/fechamento, composição (titular/substituto), APR, ocorrências, relatório diário e registro de
  obra não programada.
- Visita prévia lançada no app (mesmo checklist da etapa 1).
- Solicitação de material complementar a partir do app, vinculada à programação.
- Status em tempo real da equipe (trabalhando, em rota, aguardando, na base, sem turno) derivado de turno + atividade no app +
  telemetria.
- Revisão e avaliação de fotos no web: aprovar/reprovar com motivo, inconformidades, ranking de qualidade por equipe; só fotos
  aprovadas entram no relatório de medição detalhado.
- Georreferenciamento dos pontos a partir da posição registrada na execução, alimentando o mapa de obras.

## Capabilities

### New Capabilities
- `execucao-campo-app`: agenda, projeto offline, marcação de execução e sincronização no app.
- `evidencias-fotograficas-obra`: captura, anexação, revisão, inconformidades e ranking de fotos.
- `turno-campo-obras`: turno de Obras operado no app (APR, ocorrências, relatório, obra não programada, visita prévia, material).
- `status-tempo-real-equipes`: status operacional da equipe combinando turno, app e telemetria.

### Modified Capabilities
<!-- Depende das capabilities da change evoluir-processo-obras (turnos-obras, planejamento-pdf-obra, medicoes-obras),
     que serão MODIFIED quando a etapa 1 for arquivada e as specs principais existirem. -->

## Impact

- App `mobile/`: novas telas e armazenamento offline (pontos, vãos, itens programados, fila de apontamentos e fotos).
- Backend: endpoints de sincronização de apontamentos e fotos idempotentes, revisão de fotos, status de equipe.
- Web: revisão/avaliação de fotos, ranking, ajustes no relatório de medição detalhado para usar só fotos aprovadas.
- Pré-requisito: change `evoluir-processo-obras` implementada (apontamento de execução, turnos de Obras, planejamento).
