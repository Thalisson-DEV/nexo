## ADDED Requirements

### Requirement: Agenda da equipe no app
O app SHALL mostrar à equipe logada as programações do dia e dos próximos dias com obra, município, itens, condições
operacionais (linha viva, desligamento, turno) e acesso à rota até a obra, disponíveis offline após a sincronização inicial.

#### Scenario: Abrir agenda sem sinal
- **WHEN** o eletricista abre a agenda sem conexão depois de ter sincronizado pela manhã
- **THEN** o app mostra as programações baixadas, indicando o horário da última sincronização

### Requirement: Projeto da obra offline
O app SHALL baixar o PDF mestre e o planejamento das obras programadas para a equipe e exibi-los com pontos, vãos e status dos
itens, sem conexão.

#### Scenario: Ver ponto no projeto
- **WHEN** o eletricista toca no ponto 4 do projeto offline
- **THEN** o app mostra os kits e itens programados do ponto com quantidades previstas e executadas

### Requirement: Marcar execução em campo
O app SHALL permitir marcar ponto, vão ou item como feito, total ou parcialmente, registrando quantidade, data/hora, posição GPS,
turno e usuário, gerando um apontamento de execução idempotente. Itens não programados para a equipe MUST NOT ser marcados.

#### Scenario: Marcar ponto feito offline
- **WHEN** o eletricista marca o ponto 4 como feito sem conexão
- **THEN** o apontamento aparece como "salvo no dispositivo" e depois "enfileirado para sincronização", nunca como concluído no
  servidor antes de sincronizar

#### Scenario: Sincronização com conflito
- **WHEN** o item foi retirado da programação no web antes da sincronização do apontamento
- **THEN** o apontamento fica como "conflito de sincronização" com o motivo, sem alterar o item no servidor

### Requirement: Georreferenciamento pela execução
A posição do primeiro apontamento de cada ponto SHALL ser guardada como coordenada executada do ponto e usada no mapa de obras.

#### Scenario: Ponto sem coordenada
- **WHEN** o ponto 4 recebe o primeiro apontamento com GPS
- **THEN** o ponto passa a ter coordenada executada visível no mapa
