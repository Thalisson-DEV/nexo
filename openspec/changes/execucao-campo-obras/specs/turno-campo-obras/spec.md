## ADDED Requirements

### Requirement: Turno de Obras no app
O app SHALL permitir ao líder abrir e fechar o turno da equipe de Obras com veículo e composição (titular/substituto), offline,
usando o mesmo Turno da etapa 1.

#### Scenario: Abrir turno sem sinal
- **WHEN** o líder abre o turno sem conexão
- **THEN** o turno fica salvo no dispositivo e é sincronizado ao voltar a conexão com o horário real da abertura

### Requirement: APR, ocorrências e relatório no app
O app SHALL registrar APR (rascunho e finalização), ocorrências tipadas com fotos e o relatório diário antes do fechamento do
turno.

#### Scenario: Fechar sem relatório
- **WHEN** o líder tenta fechar o turno sem relatório diário
- **THEN** o app pede o relatório e permite fechar apenas com confirmação explícita, sinalizando "sem relatório"

### Requirement: Obra não programada
O app SHALL registrar atividade em obra não programada informando a obra e o motivo.

#### Scenario: Atividade fora da agenda
- **WHEN** a equipe atua na obra 1277970 sem programação
- **THEN** a atividade é registrada como obra não programada no turno

### Requirement: Visita prévia no app
O app SHALL lançar a visita prévia com o mesmo checklist configurado no web, com fotos e localização.

#### Scenario: Visita pelo supervisor
- **WHEN** o supervisor preenche a pré-vistoria no app
- **THEN** a obra passa a "Visita realizada" no web após a sincronização

### Requirement: Material complementar
O app SHALL permitir solicitar material complementar vinculado à programação, gerando solicitação técnica no fluxo existente.

#### Scenario: Pedir material em campo
- **WHEN** o líder solicita 2 cruzetas para a programação do dia
- **THEN** a solicitação técnica é criada após a sincronização, vinculada à obra e à programação
