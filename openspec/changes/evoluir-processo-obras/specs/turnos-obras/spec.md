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
O turno de uma equipe de Obras SHALL listar as programações do dia da equipe e as atividades que a equipe registrar em obra
não programada.

#### Scenario: Agendamento sem turno
- **WHEN** existe programação para a equipe no dia de trabalho e ela não abriu nenhum turno nesse dia
- **THEN** a equipe aparece no alerta "agendamento sem turno aberto"

### Requirement: Web só analisa o turno
O web SHALL exibir boletim, turnos e detalhe somente para consulta. Abrir e fechar turno, preencher APR, apontar, registrar ocorrência e relatório diário SHALL ser feitos pela equipe, em campo.

#### Scenario: Supervisor no detalhe do turno
- **WHEN** um usuário abre o boletim ou o detalhe de um turno no web
- **THEN** a tela mostra os registros da equipe e não oferece nenhuma ação de lançamento

### Requirement: APR obrigatória antes da execução
A equipe SHALL poder lançar a APR a qualquer hora do dia de trabalho, e mais de uma no mesmo dia. O sistema SHALL exigir APR concluída do serviço (programação) no dia antes de aceitar apontamento, salvo quando for registrado que o serviço não tem condição de ser feito; nesse caso a APR não é necessária e o apontamento fica bloqueado até uma APR concluída depois do registro ou até o registro ser desfeito.

#### Scenario: Apontar sem APR
- **WHEN** alguém aponta um item de um serviço sem APR concluída dele no dia
- **THEN** o sistema recusa e informa que a APR precisa estar concluída

#### Scenario: Dois serviços no mesmo dia
- **WHEN** a equipe conclui de manhã a APR de um serviço e, de tarde, vai executar outro
- **THEN** o segundo só é liberado depois de concluída a APR dele

#### Scenario: APR de outro dia
- **WHEN** o turno atravessa a noite e a APR foi concluída no dia anterior
- **THEN** ela não libera o apontamento do dia seguinte

#### Scenario: Condição de executar volta no mesmo dia
- **WHEN** de manhã foi registrado que não havia condição e, de tarde, a equipe conclui a APR
- **THEN** o serviço fica liberado

#### Scenario: Serviço sem condição de ser feito
- **WHEN** o turno registra o motivo de não executar numa obra
- **THEN** a APR deixa de ser cobrada dessa obra e o apontamento nela fica bloqueado

#### Scenario: Verificação negativa
- **WHEN** uma verificação da APR é respondida com "Não"
- **THEN** a APR não pode ser concluída

### Requirement: APR, ocorrências e relatório diário
O turno de Obras SHALL exibir as APRs (rascunho ou finalizada), ocorrências tipadas, fotos e o relatório diário com observação
geral, lançados pela equipe; turno fechado sem relatório MUST ser sinalizado.

#### Scenario: Turno sem relatório
- **WHEN** um turno é fechado sem relatório diário
- **THEN** ele aparece como "Sem relatório" nos indicadores e na listagem

### Requirement: Apontamento da execução pela equipe
A API SHALL receber da equipe, com a permissão `obras:execucao:write`, a quantidade executada de itens programados, vinculando ao
turno e à data. O web não aponta. A quantidade executada acumulada MUST NOT ultrapassar a prevista sem justificativa registrada.

#### Scenario: Concluir item
- **WHEN** a equipe aponta a quantidade total prevista de um item
- **THEN** o item passa a Concluído e o valor executado do planejamento, da equipe e do turno é atualizado

#### Scenario: Execução acima do previsto
- **WHEN** o apontamento excede a quantidade prevista
- **THEN** o sistema exige justificativa e registra o excedente

#### Scenario: Estornar apontamento
- **WHEN** a equipe estorna um apontamento de item ainda não medido
- **THEN** a quantidade volta a ficar em aberto e o valor sai do executado

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

### Requirement: Boletim com visão por equipe
O boletim das equipes de Obras SHALL seguir a estrutura do dashboard de turnos: visão gerencial, indicadores que abrem análise,
gráfico por equipe, programação do dia com a situação da APR e cartões ou miniaturas por equipe, com status atual, programado e
executado em R$ no ciclo, no dia e no dia útil anterior, horário do turno, veículo, líder e componentes, com filtro por dia, base e
tipo de equipe.

#### Scenario: Equipe sem turno
- **WHEN** a equipe não tem turno aberto
- **THEN** o cartão aparece em estado neutro com "Sem turno aberto" e sem valores do dia
