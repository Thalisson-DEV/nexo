-- Gravidade do desvio passa a ser pré-definida no cadastro do formulário,
-- na própria opção de resposta que gera a não-conformidade (geraDesvio = true).
-- Nullable: só é preenchida quando gera_desvio = true.
ALTER TABLE opcoes_resposta
    ADD COLUMN gravidade VARCHAR(20);
