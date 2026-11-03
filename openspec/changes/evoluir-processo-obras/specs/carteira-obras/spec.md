## ADDED Requirements

### Requirement: Dados de carteira da obra
A Obra SHALL registrar, além dos dados atuais, classe de tensão (com usuário e data de quem informou), prazo, prazo regulado,
prioridade, lote previsto, valor de projeto, valor de material previsto e valor de serviço previsto.
Os dados atuais da Obra (número, título, município, base, status, subprojetos, anexos, equipes) MUST continuar funcionando.

#### Scenario: Informar classe de tensão
- **WHEN** o usuário informa 15 kV na obra
- **THEN** a obra guarda a classe, o usuário e a data, e os kits passam a ser filtrados por 15 kV no planejamento

### Requirement: Importação da carteira
O sistema SHALL importar a carteira de obras por planilha e contrato, criando obras novas e atualizando as existentes pelo número
do processo, com histórico de importações (data, arquivo, usuário, inseridos, atualizados, erros e detalhe dos erros).

#### Scenario: Reimportar carteira
- **WHEN** o usuário importa uma planilha com obras já existentes
- **THEN** os dados de carteira são atualizados sem apagar planejamento, programação ou medições dessas obras

### Requirement: Importação de serviços e orçamento previstos
O sistema SHALL importar, por obra, os serviços previstos com preço e os valores de material e serviço do orçamento, para
comparação com o planejamento na aba de orçamento.

#### Scenario: Obra sem orçamento importado
- **WHEN** o usuário abre a aba de orçamento de uma obra sem importação
- **THEN** o sistema mostra estado vazio informando que não há orçamento importado

### Requirement: Deslocamento a partir da base
O sistema SHALL calcular e guardar a distância rodoviária e o tempo de deslocamento entre a base operacional de referência e as
coordenadas da obra, recalculando quando a base ou as coordenadas mudarem. Sem coordenadas, o deslocamento MUST aparecer como
não calculado, sem bloquear o cadastro.

#### Scenario: Obra com coordenadas
- **WHEN** uma obra com coordenadas é vinculada à base Juazeiro
- **THEN** a obra exibe a distância em km e o tempo estimado de saída da base

### Requirement: Obras próximas
O detalhe da obra SHALL listar obras ativas em um raio configurável (padrão 10 km), para apoiar o agrupamento da programação.

#### Scenario: Listar obras próximas
- **WHEN** o usuário abre o detalhe de uma obra com coordenadas
- **THEN** o sistema lista as obras no raio com distância e status de programação

### Requirement: Listagem da carteira com indicadores
A listagem de obras SHALL mostrar indicadores do recorte filtrado (quantidade, valor de projeto, material e serviço previstos,
municípios) e, por obra, status da obra, status do pré-fechamento, valor total planejado, % programado e % concluído.

#### Scenario: Obra sem kits
- **WHEN** a obra ainda não tem kits aplicados
- **THEN** a coluna de valor planejado mostra "Sem kits" e os percentuais ficam vazios

### Requirement: Status operacional da obra
A carteira SHALL usar o mesmo status da obra, sem campo de situação paralelo. O usuário SHALL poder informar o status e uma
observação, com histórico de quem alterou e quando. A coluna de status ou situação da planilha da carteira MUST atualizar esse
status e MUST apontar um status cadastrado.

#### Scenario: Informar conclusão
- **WHEN** o usuário informa a obra como concluída com observação
- **THEN** o status e a observação ficam no histórico e aparecem na listagem
