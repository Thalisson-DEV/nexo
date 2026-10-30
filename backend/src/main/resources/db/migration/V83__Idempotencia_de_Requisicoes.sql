-- V83: uma requisição de escrita repetida deixa de virar um registro repetido.
--
-- O caso que originou esta tabela: em 06/08/2026 um lançamento de desconto foi gravado
-- três vezes. Não houve concorrência nem bug de domínio — houve o comportamento normal de
-- quem não recebe resposta. O anexo falhava no proxy, o formulário mostrava erro, o usuário
-- apertava Salvar de novo, e o backend — que não tem como distinguir "outro desconto igual"
-- de "o mesmo desconto de novo" — obedecia. Somava-se a isso a fila offline do frontend,
-- que reenviava POSTs cujo desfecho ela desconhecia (erro de rede é ausência de resposta,
-- não prova de que nada aconteceu).
--
-- A correção não pode morar no formulário de desconto: qualquer tela que crie algo tem o
-- mesmo buraco. Então mora aqui, no protocolo. O cliente carimba a intenção com uma chave
-- estável (header Idempotency-Key) e o servidor promete: para uma mesma chave, executa uma
-- vez e repete a mesma resposta quantas vezes for perguntado.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Por que a chave é a PRIMARY KEY, e não uma coluna comum
-- ─────────────────────────────────────────────────────────────────────────────
-- A exclusão mútua entre duas requisições simultâneas com a mesma chave é o INSERT em si:
-- quem insere primeiro executa, quem colide já sabe que perdeu. Um SELECT-antes-de-INSERT
-- teria janela entre a leitura e a escrita — exatamente a janela do duplo clique, que é
-- medida em milissegundos. Nenhum lock de aplicação é necessário; o índice único é o lock.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Por que guardar a resposta, e não apenas a marca de "já processei"
-- ─────────────────────────────────────────────────────────────────────────────
-- Quem retenta precisa do corpo: o POST de desconto devolve o id que os passos seguintes
-- (itens, anexos) consomem. Se a retentativa recebesse só um 200 vazio, o cliente ficaria
-- sem o id e o fluxo travaria no mesmo ponto. Repetindo a resposta original, a retentativa
-- é indistinguível de um sucesso de primeira viagem — que é a única forma de o cliente não
-- precisar de código especial para o caminho de retentativa.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Por que a chave é escopada por usuário
-- ─────────────────────────────────────────────────────────────────────────────
-- A chave vem do cliente. Sem escopo, uma chave adivinhada devolveria a alguém a resposta
-- gravada de outra pessoa — que carrega dados de negócio. O id do usuário entra como coluna
-- verificada na releitura: chave de terceiro é tratada como chave inexistente.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Por que a linha expira
-- ─────────────────────────────────────────────────────────────────────────────
-- A promessa de idempotência tem prazo — depois dele, "salvar de novo" volta a significar
-- "quero outro registro", que é a intenção legítima de quem lança dois descontos parecidos
-- em dias diferentes. 24 horas cobrem com folga a janela real (retentativa de usuário e
-- fila offline que espera a conectividade voltar) sem transformar a tabela em arquivo morto.
--
-- NÃO estende BaseEntity de propósito: isto é registro de protocolo HTTP, não entidade de
-- negócio. Não tem soft delete (a linha some no expurgo, e deve mesmo sumir), não tem
-- auditoria JSONB (auditar o registro-de-que-algo-foi-auditado não acrescenta trilha) e não
-- tem optimistic locking (a única concorrência possível é a do INSERT, já resolvida pela PK).

CREATE TABLE idempotencia_requisicoes (
    chave VARCHAR(128) PRIMARY KEY,
    id_usuario UUID NOT NULL,

    -- Método + caminho + digest do corpo formam a impressão digital da requisição
    -- original. Servem para recusar o reuso de uma mesma chave para outra operação:
    -- devolver a resposta do desconto A para quem pediu o desconto B seria pior que
    -- duplicar — seria mentir sobre o que foi gravado.
    metodo VARCHAR(10) NOT NULL,
    caminho VARCHAR(512) NOT NULL,
    digest_requisicao VARCHAR(64),

    -- EM_PROCESSAMENTO: reserva feita, handler ainda rodando.
    -- CONCLUIDA: resposta abaixo é a verdade final desta chave.
    -- Não há estado de falha: requisição que termina em erro APAGA a própria linha,
    -- porque um 400 é resposta sobre a tentativa, não sobre a intenção — quem corrige
    -- o formulário e reenvia com a mesma chave tem direito a ser processado.
    estado VARCHAR(20) NOT NULL,

    status_http INTEGER,
    content_type VARCHAR(120),
    corpo_resposta TEXT,

    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_conclusao TIMESTAMP WITH TIME ZONE,
    data_expiracao TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT ck_idempotencia_estado CHECK (estado IN ('EM_PROCESSAMENTO', 'CONCLUIDA'))
);

-- O expurgo varre por data_expiracao; sem este índice ele viraria seq scan sobre uma
-- tabela que recebe uma linha por escrita do sistema inteiro.
CREATE INDEX idx_idempotencia_expiracao ON idempotencia_requisicoes (data_expiracao);

COMMENT ON TABLE idempotencia_requisicoes IS
    'Registro de requisições de escrita já processadas, endereçadas pelo header Idempotency-Key. '
    'Garante execução única e resposta repetível diante de duplo clique, retentativa de usuário '
    'e reenvio da fila offline.';

COMMENT ON COLUMN idempotencia_requisicoes.digest_requisicao IS
    'SHA-256 de metodo|caminho|corpo. Divergência com a requisição original indica reuso indevido '
    'da chave para outra operação, recusado com 422.';
