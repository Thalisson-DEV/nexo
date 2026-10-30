## ADDED Requirements

### Requirement: Canteiros de postes
O sistema SHALL manter canteiros de postes com nome, município, coordenadas, status e estoque por tipo de poste, com entrada
individual, entrada em lote, movimentações, anexos e contagem de inventário, reutilizando o estoque e as movimentações do módulo
de estoque.

#### Scenario: Contagem com divergência
- **WHEN** a contagem registra 8 postes DT 11m/600daN e o saldo é 10
- **THEN** o sistema registra a divergência como ajuste de inventário com usuário e data

### Requirement: Canteiro no mapa
Canteiros ativos com coordenadas SHALL aparecer como camada no mapa de obras e na telemetria, com total de postes.

#### Scenario: Camada de canteiros
- **WHEN** o usuário ativa a camada Canteiros
- **THEN** os canteiros aparecem com nome, município e total de postes

### Requirement: Requisição de materiais a partir da programação
O sistema SHALL gerar a solicitação técnica de materiais a partir dos itens de material das programações de uma equipe, usando o
fluxo existente de solicitação de material (Solicitado, Separado, Despachado, Em correção), com obra, equipe, data da
programação e prazo. O fluxo atual de solicitações MUST permanecer compatível.

#### Scenario: Gerar requisição da programação
- **WHEN** o usuário solicita os materiais da programação da RET-04 de 01/11
- **THEN** é criada uma solicitação técnica com os materiais e quantidades programados, vinculada à obra e à programação

### Requirement: Capacidade e jornada da equipe
A equipe ou o tipo de equipe SHALL ter jornada diária e tempo de plataforma configuráveis, usados na projeção de capacidade,
sem alterar os demais dados atuais da equipe.

#### Scenario: Sem configuração
- **WHEN** nem a equipe nem o tipo têm jornada configurada
- **THEN** a projeção usa o padrão do sistema (8h e 30 min) e indica que é valor padrão
