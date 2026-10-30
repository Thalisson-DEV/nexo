## ADDED Requirements

### Requirement: Catálogo de kits por contrato
O sistema SHALL manter kits construtivos vinculados a um contrato, cada um com código único no contrato, descrição, grupo
opcional (ex.: Poste, Estrutura, Aterramento, Condutor) e classes de tensão aplicáveis.

#### Scenario: Cadastrar kit
- **WHEN** o usuário com permissão `obras:kit:write` cadastra o kit "LMI-0688" com descrição e grupo no contrato selecionado
- **THEN** o kit fica disponível para aplicação nas obras desse contrato

#### Scenario: Código duplicado
- **WHEN** o usuário tenta cadastrar um kit com código já existente e ativo no mesmo contrato
- **THEN** o sistema rejeita a operação com um Problem Details de invariante violada

### Requirement: Itens do kit
Cada kit SHALL conter itens do tipo serviço, apontando para uma Ação do contrato, ou do tipo material, apontando para um Material,
com unidade, quantidade por kit e ação Instalar ou Retirar. O kit MUST ter ao menos um item para ser aplicado.

#### Scenario: Adicionar item de serviço
- **WHEN** o usuário adiciona ao kit a Ação "Instalar estrutura BT" com quantidade 1 e ação Instalar
- **THEN** o item aparece no kit com o valor unitário da Ação vigente no contrato

#### Scenario: Kit sem itens
- **WHEN** o usuário tenta aplicar em um ponto um kit sem itens
- **THEN** o sistema impede a aplicação e informa que o kit está vazio

### Requirement: Valoração em reais
O valor de um kit SHALL ser calculado somente em R$, somando quantidade × valor vigente da Ação para os itens de serviço.
Itens de material MUST NOT compor o valor de produção do kit.

#### Scenario: Valor com vigência
- **WHEN** a Ação de um item tem valores com vigências diferentes
- **THEN** o valor exibido usa o valor vigente na data de referência informada (hoje por padrão)

### Requirement: Importação em lote de kits
O sistema SHALL importar kits, itens de material e itens de serviço a partir de planilha, por contrato, criando ou atualizando
por código e devolvendo um relatório com inseridos, atualizados e erros por linha. Linhas com Ação ou Material inexistente MUST
ser rejeitadas sem interromper as demais.

#### Scenario: Importação parcial com erros
- **WHEN** o usuário importa uma planilha com 100 linhas, das quais 3 referenciam materiais inexistentes
- **THEN** 97 linhas são aplicadas e o relatório lista as 3 linhas com o motivo do erro

### Requirement: Manutenção e desativação de kits
O sistema SHALL permitir editar descrição, grupo, classes de tensão e itens de um kit, e desativá-lo por soft delete. Um kit em
uso em obra MUST continuar visível nas obras onde foi aplicado após a desativação, mas não pode ser aplicado em novos pontos.

#### Scenario: Desativar kit em uso
- **WHEN** o usuário desativa um kit já aplicado em pontos de obras
- **THEN** os pontos mantêm o kit e seus itens, e o kit deixa de aparecer na busca de aplicação

### Requirement: Listagem de kits
A tela de kits SHALL seguir o padrão de listagem do Nexo, com filtro por contrato, grupo, classe de tensão e busca por código ou
descrição, mostrando quantidade de itens e valor do kit.

#### Scenario: Filtrar por classe de tensão
- **WHEN** o usuário filtra por 15 kV
- **THEN** somente kits aplicáveis a 15 kV são listados
