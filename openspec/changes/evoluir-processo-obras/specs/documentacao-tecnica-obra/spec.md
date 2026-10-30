## ADDED Requirements

### Requirement: Visita prévia pelo web
O sistema SHALL permitir registrar pelo web a visita prévia da obra com checklist configurável de pré-vistoria, responsável, data
e observações, e gerar o PDF da pré-vistoria. A obra MUST indicar visita prévia "Não realizada" até o primeiro registro.

#### Scenario: Registrar visita prévia
- **WHEN** o usuário preenche o checklist e salva
- **THEN** a obra passa a "Realizada" e o PDF da pré-vistoria fica disponível

### Requirement: As Built por ponto
Para pontos com execução, o usuário SHALL preencher a ficha As Built com os dados conforme construído, marcando "Não se aplica"
nos campos sem ocorrência. Ficha incompleta MUST NOT ser incluída no documento gerado.

#### Scenario: Ficha incompleta
- **WHEN** o usuário tenta gerar o documento com um ponto de ficha incompleta
- **THEN** o sistema lista os pontos pendentes e não gera o documento

### Requirement: Plantas marcadas versionadas
O sistema SHALL gerar um documento com as páginas do projeto marcadas com os pontos e vãos executados e as fichas As Built,
salvando-o como nova versão nos anexos da obra.

#### Scenario: Gerar segunda versão
- **WHEN** o usuário gera o As Built após nova execução
- **THEN** é criada a versão 2, mantendo a versão 1 acessível

### Requirement: Book de equipamentos
O usuário SHALL marcar pontos com religador ou regulador e informar se o religador possui trafo auxiliar, consolidando a lista de
equipamentos da obra.

#### Scenario: Marcar religador
- **WHEN** o usuário marca o ponto 2 como religador com trafo auxiliar
- **THEN** o book da obra lista o ponto 2 com religador e trafo auxiliar
