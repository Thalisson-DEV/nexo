## ADDED Requirements

### Requirement: Projeto mestre da obra
Cada obra SHALL ter no máximo um anexo marcado como projeto mestre em PDF, que é a base do planejamento. Substituir o projeto
mestre MUST criar uma nova versão do anexo, preservando pontos, vãos e anotações já lançados e suas posições relativas.

#### Scenario: Substituir PDF do projeto
- **WHEN** o usuário substitui o PDF mestre de uma obra que já tem pontos
- **THEN** a nova versão passa a ser exibida, os pontos permanecem nas mesmas páginas e posições relativas e a versão anterior
  continua disponível no histórico de anexos

#### Scenario: Obra sem projeto
- **WHEN** o usuário abre o planejamento de uma obra sem PDF mestre
- **THEN** o sistema mostra estado vazio com a ação de anexar o projeto

### Requirement: Visualizador do projeto
O planejamento SHALL exibir o PDF com paginação, zoom de 25% a 400% (botões e Ctrl+roda ancorado no cursor), ajustar à largura,
arrastar a folha (ferramenta mão e botão do meio do mouse sempre disponível) e suporte a toque. A rotação de página em 90°
SHALL ser persistida no anexo para todos os usuários.

#### Scenario: Zoom ancorado
- **WHEN** o usuário aplica Ctrl+roda sobre um ponto da folha
- **THEN** o zoom muda e o mesmo ponto do desenho permanece sob o cursor

#### Scenario: Rotação compartilhada
- **WHEN** um usuário gira a página 2 em 90° à direita
- **THEN** outro usuário que abrir o mesmo projeto vê a página 2 girada, e os pontos continuam sobre os mesmos elementos do desenho

### Requirement: Pontos do levantamento
O usuário SHALL poder adicionar pontos (postes/estruturas) na folha com número, página, posição relativa (0 a 1) independente
de zoom e rotação, barramento (identificador do poste no projeto), tipo de execução (não definido, sem desligamento, com
desligamento), marcadores especiais (poda/árvore, aterramento), levantador e data. O número do ponto MUST ser único na obra.

#### Scenario: Adicionar ponto
- **WHEN** o usuário ativa "Adicionar ponto", clica na folha e informa o número 4
- **THEN** o ponto 4 aparece no local clicado, registrado com o usuário e a data

#### Scenario: Mover ponto
- **WHEN** o usuário arrasta o ponto 4 para outra posição
- **THEN** a nova posição é salva e o ponto continua com os mesmos kits

#### Scenario: Excluir ponto com itens programados
- **WHEN** o usuário tenta excluir um ponto que tem itens programados ou executados
- **THEN** o sistema impede a exclusão e informa quais itens bloqueiam

### Requirement: Vãos entre pontos
O usuário SHALL poder lançar vãos ligando dois pontos da obra, inclusive em páginas diferentes, desenhados como trechos sobre a
folha e listados como "Pa — Pb". Um vão MUST NOT ligar um ponto a ele mesmo nem duplicar um vão existente entre os mesmos pontos.

#### Scenario: Vão entre páginas
- **WHEN** o usuário conecta o ponto 12 da página 1 ao ponto 13 da página 2
- **THEN** o vão é criado e aparece sinalizado como conexão entre folhas nas duas páginas

### Requirement: Aplicação de kits em pontos e vãos
O usuário SHALL aplicar kits a pontos e vãos escolhendo a ação (Instalar ou Retirar). No ponto, o multiplicador é a quantidade de
kits e vale para todos os itens. No vão, o multiplicador é a metragem do vão e vale só para os itens medidos em metro; os demais
ficam com a quantidade do kit. A busca de kits MUST filtrar pela classe de tensão da obra, com opção de ver todos, e MUST indicar o
kit que o ponto ou vão já tem. Cada item planejado SHALL guardar quantidade prevista, executada, status (Pendente, Programado,
Em andamento, Concluído, Medido) e permitir editar quantidade de material ou remover item ainda pendente.

#### Scenario: Aplicar kit com multiplicador
- **WHEN** o usuário aplica a um vão de 35 m um kit de cabo com 1,04 m de cabo, 1,04 m de lançamento e 1 alça
- **THEN** o vão prevê 36,4 m de cabo, 36,4 m de lançamento e 1 alça

#### Scenario: Kit executado antes do sistema
- **WHEN** o usuário marca um kit sem programação como já executado antes do sistema
- **THEN** os itens saem do pendente, não podem ser programados e contam como executados nos indicadores

#### Scenario: Remover kit com item programado
- **WHEN** o usuário tenta remover um kit cujo item já está programado
- **THEN** o sistema bloqueia a remoção e indica o item e a programação

### Requirement: Aplicação em lote
Com vários pontos ou vãos selecionados, o usuário SHALL poder aplicar o mesmo kit a todos de uma vez.

#### Scenario: Aplicar kit a seleção
- **WHEN** o usuário seleciona 10 pontos por área retangular e aplica o kit de cava
- **THEN** os 10 pontos recebem o kit, e pontos que já tinham o kit são informados sem duplicação

### Requirement: Seleção de itens
O planejamento SHALL permitir selecionar pontos, vãos e itens por clique na folha, por área retangular e por caixas de seleção na
árvore lateral, mantendo um contador de pontos e itens selecionados e ação de limpar seleção (Esc).

#### Scenario: Seleção por área
- **WHEN** o usuário desenha um retângulo sobre a folha com a ferramenta de área
- **THEN** todos os pontos e vãos dentro do retângulo e seus itens pendentes ficam selecionados

### Requirement: Extração de texto do PDF
O usuário SHALL poder selecionar uma região da folha e obter o texto do PDF contido nela, ordenado por linha e coluna, para
preencher campos (ex.: barramento, busca de kit). Quando o PDF não tiver camada de texto, o sistema MUST informar que não há texto
extraível na região.

#### Scenario: Extrair barramento
- **WHEN** o usuário seleciona a região onde está escrito "K051159" e escolhe preencher o barramento do ponto
- **THEN** o campo barramento recebe "K051159"

### Requirement: Anotações
O usuário SHALL poder criar anotações de texto fixadas em uma posição da folha, editá-las e excluí-las, com autor e data visíveis.

#### Scenario: Criar anotação
- **WHEN** o usuário ativa "Anotar", clica na folha e escreve "travessia de rodovia"
- **THEN** o marcador da anotação aparece na posição com o texto, autor e data

### Requirement: Camadas e legibilidade
O planejamento SHALL permitir ocultar pontos e trechos, esmaecer o PDF em níveis para destacar os vãos e ocultar o painel lateral.
Estados de ponto (pendente, programado, em andamento, concluído, atrasado, selecionado) MUST ser distinguíveis por forma ou ícone
além da cor.

#### Scenario: Esmaecer PDF
- **WHEN** o usuário aumenta o esmaecido
- **THEN** o desenho do PDF fica mais claro e os pontos e vãos permanecem com contraste total

### Requirement: Árvore do planejamento
O painel lateral SHALL listar Página → Ponto → Kit → itens e a seção de Vãos, com busca por ponto, material ou serviço, alternância
"só serviços" / "serviço + material", resumo de pontos e vãos sem kit e, em cada item, a programação vinculada (equipe e período)
com ação para removê-lo da programação.

#### Scenario: Buscar serviço
- **WHEN** o usuário busca "cava em rocha"
- **THEN** a árvore mostra apenas os pontos e itens que contêm esse serviço

### Requirement: Indicadores do planejamento
O cabeçalho do planejamento SHALL mostrar % executado e valores em R$ executado, programado, pendente e total; o painel
"Avanço por kit" SHALL mostrar, por kit, % e valor executado sobre o total.

#### Scenario: Programar itens atualiza indicadores
- **WHEN** itens de R$ 10.077 passam a programados
- **THEN** o valor programado do cabeçalho aumenta em R$ 10.077 e o pendente diminui no mesmo valor

### Requirement: Concorrência e trilha de auditoria
Alterações de planejamento SHALL usar controle otimista de versão; quando outro usuário alterou o mesmo ponto, vão ou item, o
sistema MUST recusar a gravação e recarregar o estado atual informando o conflito. Toda alteração fica no log de auditoria.

#### Scenario: Edição simultânea
- **WHEN** dois usuários editam o barramento do mesmo ponto e o segundo salva depois do primeiro
- **THEN** o segundo recebe aviso de conflito com o valor atual e nada é sobrescrito sem confirmação

### Requirement: Detalhe do ponto
O ponto SHALL abrir um resumo rápido sobre a folha (tipo de execução, barramento, kits, ações de mover, conectar, fotos e
expandir) e um detalhe completo com abas Kits construtivos e Orçamento.

#### Scenario: Abrir detalhe completo
- **WHEN** o usuário expande o resumo do ponto 4
- **THEN** o detalhe mostra os kits com itens, previsto e executado por item, status e a aba de orçamento da obra

### Requirement: Planejamento assistido pelo Copiloto
O usuário SHALL poder pedir ao Copiloto que leia uma página do projeto como imagem e proponha pontos (poste, transformador, chave,
estrutura), número, posição, barramento, vãos com metragem e kits candidatos. O resultado MUST ser uma proposta: nada entra no
planejamento sem revisão, cada sugestão mostra de onde veio e a confiança, e pode ser aceita, ajustada ou descartada, uma a uma ou
em conjunto. Sem modelo com visão configurado, o sistema MUST informar a indisponibilidade e manter o planejamento manual.

#### Scenario: Revisar proposta
- **WHEN** a análise de uma página termina com 12 pontos e 11 vãos sugeridos
- **THEN** as sugestões aparecem sobre a folha com aparência distinta dos pontos reais, e só as aceitas são gravadas

#### Scenario: Número já usado
- **WHEN** a análise sugere um ponto cujo número já existe na obra
- **THEN** a sugestão vem marcada como conflito e não é gravada até o usuário definir outro número

#### Scenario: Leitura incerta
- **WHEN** o modelo devolve um kit candidato com baixa confiança ou que não existe no contrato
- **THEN** o kit não vem marcado para aceite e o texto lido da folha continua visível para decisão do usuário
