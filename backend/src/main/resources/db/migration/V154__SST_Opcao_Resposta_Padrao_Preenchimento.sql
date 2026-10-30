-- =====================================================================
-- V154: Suporte a opção de resposta padrão nos formulários de inspeção SST
-- Permite que perguntas objetivas tenham opção pré-definida como padrão,
-- e define 'CONFORME' como padrão para os formulários já existentes.
-- =====================================================================

ALTER TABLE opcoes_resposta
    ADD COLUMN padrao BOOLEAN NOT NULL DEFAULT FALSE;

-- Para formulários existentes, define 'CONFORME' como padrão nas perguntas objetivas
UPDATE opcoes_resposta o
   SET padrao = TRUE
  FROM perguntas_inspecao p
 WHERE o.id_pergunta = p.id
   AND p.tipo_resposta IN ('MULTIPLA_ESCOLHA', 'MULTIPLA_SELECAO')
   AND UPPER(TRIM(o.texto)) = 'CONFORME'
   AND o.gera_desvio = FALSE;
