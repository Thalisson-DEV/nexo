## ADDED Requirements

### Requirement: Turno no processo de Obras
O Turno existente SHALL atender também equipes do processo de Obras, mantendo abertura, fechamento, equipe, veículo e
colaboradores. O comportamento dos turnos do processo comercial MUST permanecer inalterado.

#### Scenario: Turno comercial inalterado
- **WHEN** uma equipe do processo comercial abre e fecha um turno
- **THEN** o fluxo, os dados e os relatórios atuais continuam iguais

### Requirement: Composição com titular e substituto
O turno SHALL registrar o líder de turma e, para cada componente, se atua como titular da equipe ou como substituto.

#### Scenario: Substituto no turno
- **WHEN** um colaborador de outra equipe entra no turno da LM-20
- **THEN** ele aparece como substituto na composição e no dashboard de turnos

### Requirement: Vínculo com programações
O turno de uma equipe de Obras SHALL listar as programações do dia da equipe e permitir registrar atividade em obra não
programada, informando a obra.

#### Scenario: Agendamento sem turno
- **WHEN** existe programação para a equipe hoje e nenhum turno aberto
- **THEN** a equipe aparece no alerta "agendamento sem turno aberto"

### Requirement: APR, ocorrências e relatório diário
O turno de Obras SHALL exibir as APRs (rascunho ou finalizada), ocorrências tipadas, fotos e o relatório diário com observação
geral; turno fechado sem relatório MUST ser sinalizado.

#### Scenario: Turno sem relatório
- **WHEN** um turno é fechado sem relatório diário
- **THEN** ele aparece como "Sem relatório" nos indicadores e na listagem

### Requirement: Apontamento web da execução
Usuário com permissão `obras:execucao:write` SHALL apontar pelo web a quantidade executada de itens programados, vinculando ao
turno e à data. A quantidade executada acumulada MUST NOT ultrapassar a prevista sem justificativa registrada.

#### Scenario: Concluir item
- **WHEN** o supervisor aponta a quantidade total prevista de um item
- **THEN** o item passa a Concluído e o valor executado do planejamento, da equipe e do turno é atualizado

#### Scenario: Execução acima do previsto
- **WHEN** o apontamento excede a quantidade prevista
- **THEN** o sistema exige justificativa e registra o excedente

### Requirement: Desempenho do dia
O detalhe do turno SHALL mostrar realizado em R$ × meta do dia da equipe, % atingido e os serviços executados no turno.

#### Scenario: Meta batida
- **WHEN** o realizado do turno é maior ou igual à meta do dia
- **THEN** o turno conta como "bateu meta" nos indicadores

### Requirement: Listagem de turnos de Obras
A listagem de turnos SHALL permitir filtrar por equipe, obra, período, status (aberto/fechado), meta (bateu/não bateu) e base, com
indicadores de turnos, em andamento, horas totais, bateu meta, sem relatório, com ocorrências e serviços executados.

#### Scenario: Filtrar turnos de uma obra
- **WHEN** o usuário filtra pela obra 939550
- **THEN** só turnos com atividade nessa obra aparecem

### Requirement: Dashboard de turnos com visão por equipe
O dashboard de turnos existente SHALL oferecer a visão por equipe (cartões) para equipes de Obras: status atual, programado e
executado em R$ acumulado no ciclo, no dia e no dia anterior, data do turno aberto, veículo, líder e componentes, com filtro por
base e alternância entre visão detalhada e painel compacto.

#### Scenario: Equipe sem turno
- **WHEN** a equipe não tem turno aberto
- **THEN** o cartão aparece em estado neutro com "Sem turno aberto" e sem valores do dia
