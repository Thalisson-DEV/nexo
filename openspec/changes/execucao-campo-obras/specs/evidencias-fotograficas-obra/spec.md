## ADDED Requirements

### Requirement: Evidências por ponto e vão
O app SHALL exigir ou sugerir, conforme configuração do contrato, fotos ao marcar ponto ou vão como feito, guardando-as offline e
enviando-as na sincronização vinculadas ao ponto/vão e ao apontamento.

#### Scenario: Foto obrigatória
- **WHEN** a configuração exige foto e o eletricista tenta marcar o vão sem foto
- **THEN** o app impede a marcação e pede a foto

### Requirement: Anexação de evidência pelo web
O web SHALL permitir anexar fotos a pontos e vãos executados (ex.: execução anterior à adoção do app), identificadas como
anexação manual.

#### Scenario: Anexar foto manual
- **WHEN** o usuário anexa uma foto ao ponto 2 pelo web
- **THEN** a foto aparece nas evidências do ponto marcada como anexação manual

### Requirement: Revisão de fotos
O usuário com permissão de revisão SHALL aprovar ou reprovar cada foto com motivo; somente fotos aprovadas MUST entrar no
relatório de medição detalhado.

#### Scenario: Reprovar foto
- **WHEN** o revisor reprova uma foto por "foto desfocada"
- **THEN** a foto sai do relatório detalhado e aparece em inconformidades

### Requirement: Inconformidades e ranking
O sistema SHALL listar inconformidades de fotos por período, equipe e motivo, e o ranking de qualidade das fotos por equipe
(percentual de aprovação).

#### Scenario: Ranking do mês
- **WHEN** o usuário abre o ranking de outubro
- **THEN** as equipes aparecem ordenadas pelo percentual de fotos aprovadas
