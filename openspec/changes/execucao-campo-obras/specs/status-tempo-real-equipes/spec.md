## ADDED Requirements

### Requirement: Status operacional da equipe
O sistema SHALL derivar o status da equipe entre Sem turno, Na base, Em rota, Aguardando e Trabalhando, combinando turno aberto,
última atividade no app e posição/ignição/movimento da telemetria, exibindo-o na central de operações, no dashboard de turnos e
na telemetria.

#### Scenario: Equipe em rota
- **WHEN** a equipe tem turno aberto e o veículo está em movimento fora do raio da base
- **THEN** o status exibido é "Em rota"

#### Scenario: Trabalhando
- **WHEN** a equipe registrou apontamento nos últimos minutos configurados e o veículo está parado perto da obra
- **THEN** o status exibido é "Trabalhando"

### Requirement: Atualização do status
O status SHALL ser recalculado a cada nova posição de telemetria ou evento do app, com o horário da última atualização visível.

#### Scenario: Sem dados recentes
- **WHEN** não há posição nem atividade dentro do limite configurado
- **THEN** o status mantém o último valor marcado como desatualizado com o horário
