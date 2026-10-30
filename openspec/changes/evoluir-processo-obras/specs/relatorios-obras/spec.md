## ADDED Requirements

### Requirement: Avaliação da carteira
O sistema SHALL apresentar o esforço de material por obra, prazos e distribuição geográfica da carteira, filtrável por contrato,
base, município e status.

#### Scenario: Filtrar por município
- **WHEN** o usuário filtra por um município
- **THEN** os indicadores e o mapa refletem só as obras do município

### Requirement: Programação e programado × executado
O sistema SHALL emitir a agenda de equipes e ocupação por período e o comparativo programado × executado por equipe e dia, com
totais do período e exportação para Excel.

#### Scenario: Exportar comparativo
- **WHEN** o usuário exporta o programado × executado de outubro
- **THEN** o arquivo traz uma linha por equipe e dia com valores e percentuais

### Requirement: Execução por turno e prazos
O sistema SHALL emitir serviços e kits executados por turno no período e as obras por prazo regulado classificadas em vencidas,
urgentes e no prazo.

#### Scenario: Obra vencida
- **WHEN** o prazo regulado da obra já passou e ela não está concluída
- **THEN** a obra aparece como vencida

### Requirement: Produtividade e financeiro
O sistema SHALL emitir produtividade por equipe e por colaborador (ritmo de execução em R$ por dia trabalhado) e o relatório
financeiro com meta, programado, executado e projeção por equipe.

#### Scenario: Produtividade por colaborador
- **WHEN** o usuário gera a produtividade por colaborador
- **THEN** cada colaborador aparece com dias em turno e valor executado das equipes em que atuou

### Requirement: APRs do processo de Obras
O sistema SHALL listar as APRs dos turnos de Obras por período, equipe e situação (rascunho ou finalizada).

#### Scenario: APRs em rascunho
- **WHEN** o usuário filtra APRs em rascunho
- **THEN** só aparecem APRs não finalizadas com turno e equipe
