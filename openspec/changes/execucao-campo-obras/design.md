## Context

Etapa 2 do processo de Obras. Depende da change `evoluir-processo-obras` (planejamento, programação, ApontamentoExecucao,
turnos de Obras, medições). O app `mobile/` é offline-first, com fila de sincronização e vocabulário normativo de estados.

## Goals / Non-Goals

**Goals:**
- Execução em campo sem sinal, com o mesmo modelo de apontamento da etapa 1.
- Evidências confiáveis (vínculo ponto/vão/apontamento, GPS, horário) e revisão antes da medição.
- Status de equipe confiável combinando app e telemetria.

**Non-Goals:**
- Edição de planejamento no app (pontos, vãos e kits só no web).
- Valoração em US.

## Decisions

- **Apontamento idempotente**: o app gera um UUID por apontamento; o backend aceita reenvio sem duplicar. Alternativa (chave
  composta item+data) rejeitada por permitir perda de apontamentos parciais no mesmo dia.
- **Pacote offline por equipe**: download incremental das obras programadas (PDF + planejamento compacto da etapa 1), com limite
  de armazenamento e limpeza de obras concluídas.
- **Fotos**: compressão no dispositivo, envio em fila separada (`AnexoSyncService` existente), metadados de GPS/horário do
  dispositivo e do servidor.
- **Conflitos**: o servidor valida programação e saldo no recebimento; rejeição vira estado `conflict` no app com motivo, sem
  sobrescrever dados do web.
- **Status da equipe**: cálculo no backend a partir de eventos (turno, apontamento, telemetria) com regras configuráveis (raio da
  base, raio da obra, minutos de inatividade).

## Risks / Trade-offs

- [Armazenamento do aparelho com PDFs grandes] → baixar só obras programadas, limpar ao concluir, avisar quando faltar espaço.
- [GPS impreciso] → guardar precisão informada e não georreferenciar ponto com precisão acima do limite.
- [Relógio do aparelho errado] → guardar hora do dispositivo e do servidor; relatórios usam a do servidor quando divergirem.
