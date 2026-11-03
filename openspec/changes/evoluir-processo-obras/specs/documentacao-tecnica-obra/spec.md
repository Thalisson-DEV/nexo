## ADDED Requirements

### Requirement: Modelos configuráveis de checklist
O sistema SHALL permitir configurar modelos de checklist da visita prévia e da ficha As Built, no padrão dos formulários do SST: grupos, perguntas, tipo de resposta, obrigatoriedade e "Não se aplica". O modelo da ficha SHALL também listar as fotos pedidas em cada ponto. Modelo já usado MUST NOT ter as perguntas nem as fotos pedidas alteradas; para mudá-las, duplica-se o modelo.

#### Scenario: Alterar modelo em uso
- **WHEN** o administrador abre um modelo que já tem lançamento
- **THEN** só título e descrição podem mudar, e o que já foi lançado continua como foi respondido

### Requirement: Lançamento pelo supervisor
O supervisor SHALL lançar visita prévia, fichas As Built e book de equipamentos pelo web e pelo app, com os mesmos comandos da API.

#### Scenario: Mesmo comando no web e no app
- **WHEN** o supervisor registra uma visita pelo web ou pelo app
- **THEN** o registro é o mesmo e aparece na documentação da obra

### Requirement: Visita prévia
O sistema SHALL permitir registrar a visita prévia da obra com checklist configurável de pré-vistoria, responsável, data
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

### Requirement: Book da obra
O sistema SHALL guardar as fotos de cada ponto, por item pedido no modelo ou avulsas, com legenda, data e autor, e SHALL permitir vê-las no ponto. A foto SHALL poder ser lançada em qualquer ponto, com ou sem execução, e MUST ser JPG ou PNG. O supervisor e o administrativo lançam pelo web; a equipe lança pelo app, com os mesmos comandos da API.

#### Scenario: Lançar foto do item pedido
- **WHEN** o usuário lança uma foto no item "Aterramento" do ponto 1
- **THEN** a foto aparece no item e na visualização do ponto, com data e autor

#### Scenario: Foto antes da execução
- **WHEN** o usuário lança uma foto em um ponto sem execução
- **THEN** a foto é guardada, e a ficha do ponto continua indisponível até haver execução

#### Scenario: Gerar com foto faltando
- **WHEN** o usuário gera o As Built com as fichas completas e um ponto executado sem foto ou com item obrigatório sem foto
- **THEN** o sistema lista os pontos e pede confirmação, e o documento sai com as fotos já lançadas

### Requirement: Plantas marcadas versionadas
O sistema SHALL gerar um documento com as páginas do projeto marcadas com os pontos e vãos executados, as fichas As Built e as fotos do book,
salvando-o como nova versão nos anexos da obra.

#### Scenario: Gerar segunda versão
- **WHEN** o usuário gera o As Built após nova execução
- **THEN** é criada a versão 2, mantendo a versão 1 acessível

### Requirement: Equipamentos da obra
O usuário SHALL marcar pontos com religador ou regulador e informar se o religador possui trafo auxiliar, consolidando a lista de
equipamentos da obra.

#### Scenario: Marcar religador
- **WHEN** o usuário marca o ponto 2 como religador com trafo auxiliar
- **THEN** a lista de equipamentos da obra traz o ponto 2 com religador e trafo auxiliar
