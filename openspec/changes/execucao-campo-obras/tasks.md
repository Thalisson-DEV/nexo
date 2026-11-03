<!-- Pré-requisito: change evoluir-processo-obras implementada (frentes A, B, C e D no mínimo). -->

## 1. Backend de sincronização

- [ ] 1.1 Endpoint de pacote offline da equipe (programações, planejamento compacto, PDFs) com download incremental
- [ ] 1.2 Recebimento idempotente de apontamentos com validação de programação/saldo e resposta de conflito
- [ ] 1.3 Recebimento de fotos vinculadas a ponto/vão/apontamento com metadados
- [ ] 1.4 Coordenada executada do ponto a partir do primeiro apontamento com GPS

## 2. App: execução

- [ ] 2.1 Agenda da equipe offline
- [ ] 2.2 Visualizador do projeto no app com pontos, vãos e status
- [ ] 2.3 Marcar ponto/vão/item feito (total/parcial) com estados normativos de sincronização
- [ ] 2.4 Captura de fotos obrigatórias/opcionais por configuração do contrato

## 3. App: turno de Obras

- [ ] 3.1 Abrir/fechar turno com composição titular/substituto
- [ ] 3.2 APR, ocorrências e relatório diário, usando os comandos de `TurnoObraController`. A equipe lança a APR a qualquer hora do dia e pode lançar mais de uma: cada uma informa o serviço (`idProgramacao`), e a API exige APR concluída do serviço no dia para apontar. Registrar "sem condição de executar" (ocorrência com `impedeExecucao`) dispensa a APR; uma APR concluída depois libera. Formulário: atividade, local, responsável, riscos, medidas de controle, verificações Sim/Não/Não se aplica (resposta "Não" impede concluir), participantes e documento opcional. Opções sugeridas, a validar com o SST: riscos (choque elétrico, arco elétrico, queda de altura, queda de materiais ou ferramentas, movimentação de carga, tráfego, escavação, animais peçonhentos, clima, calor ou esforço físico); medidas (desenergização, bloqueio e aterramento, linha viva, sinalização e isolamento, inspeção de escada e cinto, plano de içamento, EPI e EPC, revezamento e hidratação); verificações (conversa sobre riscos, EPI em bom estado, ferramentas e veículo inspecionados, área sinalizada, clima permite, desligamento confirmado).
- [ ] 3.4 Criar o perfil da equipe de campo com `obras:turno:write` e `obras:execucao:write` (hoje nenhum perfil tem) e informar líder e substitutos ao abrir o turno (`Turno.informarComposicao`)
- [ ] 3.3 Obra não programada e material complementar
- [ ] 3.5 Telas do supervisor no app para visita prévia, ficha As Built e book, usando `DocumentacaoTecnicaController` e os modelos de `/v1/obras/checklists`

## 4. Web: revisão de fotos

- [ ] 4.1 Anexação manual de evidências a pontos/vãos
- [ ] 4.2 Revisão (aprovar/reprovar com motivo) e inconformidades
- [ ] 4.3 Ranking de qualidade por equipe
- [ ] 4.4 Relatório de medição detalhado usando só fotos aprovadas

## 5. Status em tempo real

- [ ] 5.1 Cálculo de status a partir de turno, apontamentos e telemetria com regras configuráveis
- [ ] 5.2 Exibição na central de operações, dashboard de turnos e telemetria

## 6. Fechamento

- [ ] 6.1 Testes offline (sem sinal, reconexão, conflito) em dispositivo real
- [ ] 6.2 Build/testes do app, backend e web; documentação
