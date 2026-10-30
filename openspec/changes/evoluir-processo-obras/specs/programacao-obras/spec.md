## ADDED Requirements

### Requirement: Programar itens planejados
A partir da seleção no planejamento, o usuário com permissão `obras:programacao:write` SHALL criar uma programação ligando os
itens selecionados a uma equipe e a um período (início e fim). Itens já programados ou concluídos MUST ser ignorados e informados
antes da confirmação.

#### Scenario: Programar com itens já programados
- **WHEN** o usuário seleciona 48 itens, dos quais 40 já estão programados, e confirma para a equipe RET-04
- **THEN** somente os 8 pendentes são programados e o aviso informa os 40 ignorados

### Requirement: Condições operacionais da programação
A programação SHALL exigir descrição do planejamento, linha viva (sim/não), desligamento (sim/não), finaliza obra (sim/não),
turno (dia todo, manhã, tarde) e situação da intervenção (Criado, Pendente, Não se aplica), com observação opcional.

#### Scenario: Campos obrigatórios ausentes
- **WHEN** o usuário confirma sem informar desligamento
- **THEN** o sistema impede a confirmação e destaca o campo

### Requirement: Escolha de equipe com filtros
O diálogo de programação SHALL listar equipes filtráveis por base operacional e tipo de equipe, mostrando para cada uma a
ocupação da meta no período e a distância/tempo da base até a obra.

#### Scenario: Filtrar por tipo
- **WHEN** o usuário filtra por Retroescavadeira e base Juazeiro
- **THEN** só equipes desse tipo e base aparecem, com a barra de ocupação do período

### Requirement: Projeção de capacidade
Ao escolher uma equipe, o sistema SHALL projetar a capacidade diária líquida = jornada da equipe − tempo de plataforma −
deslocamento ida e volta até a obra, e alertar quando a capacidade for menor ou igual a zero. Jornada e tempo de plataforma MUST
vir da configuração da equipe ou do tipo de equipe, não de valor fixo.

#### Scenario: Deslocamento maior que a jornada
- **WHEN** a jornada é 8h, a plataforma 30 min e o deslocamento ida e volta 9h16
- **THEN** a capacidade líquida aparece negativa com alerta de inviabilidade, sem impedir a programação

### Requirement: Meta financeira do período
Ao escolher uma equipe, o sistema SHALL mostrar meta por dia útil, meta do período (meta/dia × dias úteis), valor já programado no
período, valor desta programação e % da meta, usando a meta vigente da equipe.

#### Scenario: Programação acima da meta
- **WHEN** a soma programada no período ultrapassa 100% da meta
- **THEN** o percentual é destacado como acima da meta

### Requirement: Valor congelado da programação
Ao confirmar, a programação SHALL guardar o valor de cada item de serviço calculado pelo valor vigente da Ação na data de início
da programação.

#### Scenario: Reajuste posterior
- **WHEN** o valor da Ação é reajustado depois da programação
- **THEN** o valor da programação existente não muda

### Requirement: Editar, reprogramar, cancelar e excluir
O usuário SHALL poder editar dados, trocar equipe ou período (reprogramar), cancelar e excluir uma programação, com observação
obrigatória em reprogramação e cancelamento. Itens de uma programação excluída ou cancelada MUST voltar a pendentes, exceto os
já executados.

#### Scenario: Reprogramar para outra equipe
- **WHEN** o usuário move a programação #1687 da equipe LVL-02 para LNV-02 informando "turma errada"
- **THEN** a programação passa à nova equipe e o histórico registra origem, destino, períodos e observação

### Requirement: Histórico de reprogramações
O sistema SHALL listar todas as edições, reprogramações, cancelamentos e exclusões de programações com data/hora, tipo,
programação, obra, equipe origem e destino, período original e novo, usuário e observação, filtrável por tipo e por texto.

#### Scenario: Filtrar excluídas
- **WHEN** o usuário filtra por "Excluídos"
- **THEN** somente eventos de exclusão aparecem

### Requirement: Visão Gantt
O sistema SHALL exibir as programações em linha do tempo por equipe, com navegação por período, cores por status e acesso ao
planejamento da obra. A tela MUST carregar diretamente pela URL com o período e os filtros nela.

#### Scenario: Abrir Gantt pela URL
- **WHEN** o usuário abre o endereço do Gantt com período e base na URL
- **THEN** a linha do tempo carrega já filtrada

### Requirement: Remover item de uma programação
Pelo planejamento, o usuário SHALL poder retirar um item pendente de execução de sua programação, voltando-o a pendente.

#### Scenario: Retirar item executado
- **WHEN** o usuário tenta retirar um item com quantidade executada maior que zero
- **THEN** o sistema impede e informa que o item já tem execução
