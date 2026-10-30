## ADDED Requirements

### Requirement: Pré-fechamento do levantamento
O sistema SHALL mostrar, por obra, o estado do levantamento: pontos, vãos, pontos sem vão, pontos sem kit, vãos sem kit,
quantidade por tipo de execução (sem desligamento, com desligamento, não definido), levantadores e status. A obra só MUST poder
ser marcada como levantamento concluído quando não houver pontos nem vãos sem kit.

#### Scenario: Marcar concluído com pendência
- **WHEN** o usuário tenta concluir o levantamento de uma obra com 3 pontos sem kit
- **THEN** o sistema impede e lista os pontos sem kit

#### Scenario: Lista de pendentes
- **WHEN** o usuário filtra "apenas pendentes"
- **THEN** a lista mostra só obras com levantamento não concluído

### Requirement: Medição parcial
O usuário com `obras:medicao:write` SHALL gerar a medição parcial de uma obra a partir dos itens concluídos e ainda não medidos,
com valores congelados da programação. Itens medidos MUST passar a Medido e não podem entrar em outra medição.

#### Scenario: Gerar medição
- **WHEN** a obra tem 5 itens concluídos não medidos e o usuário gera a medição
- **THEN** a medição é criada com os 5 itens e eles deixam de aparecer como pendentes de medição

#### Scenario: Nada pendente
- **WHEN** todos os itens concluídos já foram medidos
- **THEN** a tela informa "nada pendente" e lista as medições já geradas

### Requirement: Estorno de medição
Uma medição SHALL poder ser estornada com justificativa, devolvendo seus itens a Concluído, mantendo o registro estornado.

#### Scenario: Estornar
- **WHEN** o usuário estorna a medição 12 com justificativa
- **THEN** os itens voltam a Concluído e a medição 12 fica como estornada no histórico

### Requirement: Medição por ciclo
O sistema SHALL gerar a medição do ciclo do contrato por obra, com valor de projeto, medido em ciclos anteriores, medido no ciclo
e saldo.

#### Scenario: Saldo do ciclo
- **WHEN** a obra tem projeto de R$ 100 mil e R$ 60 mil medidos antes
- **THEN** a medição do ciclo mostra saldo anterior de R$ 40 mil e o saldo após o ciclo

### Requirement: Relatório de medição detalhado
O sistema SHALL receber a planilha orçado × faturado do contrato (modelo baixável), identificar as obras cadastradas e gerar, para
as obras escolhidas, o relatório detalhado em XLSX e PDF com as fotos aprovadas, em um único pacote.

#### Scenario: Obra não cadastrada na planilha
- **WHEN** a planilha contém obra inexistente no contrato
- **THEN** a obra é listada como não identificada e não bloqueia as demais

### Requirement: Fechamento de obra
O sistema SHALL gerar, para uma ou várias obras (aceitando códigos colados de planilha, um por linha), a relação de materiais e
serviços lançados × concluídos com valor, o comparativo por kit e as medições parciais geradas.

#### Scenario: Vários códigos
- **WHEN** o usuário cola 20 códigos de obra e gera
- **THEN** o fechamento é gerado para as obras encontradas e os códigos inexistentes são listados
