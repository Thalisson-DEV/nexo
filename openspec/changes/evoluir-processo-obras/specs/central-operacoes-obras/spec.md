## ADDED Requirements

### Requirement: Painel do dia
A central de operações SHALL mostrar o ciclo vigente, data e hora, equipes em turno sobre o total, agendamentos de hoje, obras
pendentes de pré-fechamento, executado no mês e % da meta, e usuários online, com atualização manual e automática.

#### Scenario: Atualizar painel
- **WHEN** o usuário aciona "Atualizar"
- **THEN** todos os blocos são recarregados e o horário da última atualização é exibido

### Requirement: Alertas e pendências
O painel SHALL listar alertas acionáveis: equipes com agendamento hoje sem turno aberto, requisições de material aguardando ação,
obras pendentes de pré-fechamento e obras em risco de prazo, cada um levando à tela de tratamento.

#### Scenario: Abrir alerta
- **WHEN** o usuário clica em "1 sem turno aberto"
- **THEN** o sistema mostra a equipe e a obra agendada

### Requirement: Financeiro e projeção
O painel SHALL mostrar meta do mês, executado, programado, ritmo por dia útil, projeção de fechamento (executado + ritmo × dias
úteis restantes), quanto falta para a meta, meta por tipo de equipe (equipes, meta, programado, executado, % e projeção) e
programado dos próximos 7 dias.

#### Scenario: Projeção
- **WHEN** foram executados R$ 1 milhão em 10 dias úteis e restam 12 dias úteis
- **THEN** a projeção exibida é R$ 2,2 milhões

### Requirement: Equipes, frota e produtividade
O painel SHALL mostrar equipes em turno com valores do dia e acumulados, status da frota (na base, em rota, trabalhando,
aguardando) com obra e distância da base, turmas por tipo em turno, ranking de produtividade, equipes ociosas, tendência semanal
e taxa de conclusão dos agendamentos passados.

#### Scenario: Equipe ociosa
- **WHEN** uma equipe ativa não tem programação no dia
- **THEN** ela aparece em "Equipes ociosas"

### Requirement: Mapa de obras
O sistema SHALL exibir as obras no mapa com cor por status de execução, filtros por base e status, e, ao escolher uma base, a rota
rodoviária da base até as obras selecionadas com distância e tempo; o clique na obra mostra resumo e acesso ao planejamento.

#### Scenario: Rota da base
- **WHEN** o usuário escolhe a base Juazeiro e seleciona uma obra
- **THEN** o mapa traça a rota e mostra distância e tempo

### Requirement: Telemetria das equipes
O sistema SHALL mostrar os veículos da telemetria vinculados às equipes, com placa, equipe, categoria, ignição, movimento,
velocidade, online e última posição, atualizando periodicamente, com camadas (bases, obras, canteiros), filtro por categoria de
equipe, busca por placa e destaque das equipes programadas no dia ligadas às suas obras.

#### Scenario: Destacar turma do dia
- **WHEN** o usuário escolhe a equipe RET-04 em "turmas do dia"
- **THEN** o mapa destaca o veículo e traça linhas até as obras programadas da equipe

#### Scenario: Veículo offline
- **WHEN** o veículo não envia posição há mais que o limite configurado
- **THEN** ele aparece como offline com a última posição e horário
