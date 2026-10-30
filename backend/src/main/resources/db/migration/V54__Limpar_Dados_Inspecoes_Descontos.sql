-- V54: Limpeza de dados de teste (Inspeções e Descontos)
-- Remove apenas os dados transacionais gerados durante os testes em produção.
-- Mantém templates, perguntas, formulários, motivos e tipos de equipamento.

-- 1. Limpeza de Inspeções de Segurança (e suas correlações)
DELETE FROM anexos_desvio;
DELETE FROM desvio_colaboradores;
DELETE FROM desvios_seguranca;
DELETE FROM respostas_opcoes;
DELETE FROM respostas_inspecao;
DELETE FROM inspecao_colaboradores;
DELETE FROM inspecoes_seguranca;

-- 2. Limpeza de Descontos (e suas correlações)
DELETE FROM desconto_item;
DELETE FROM desconto_anexo;
DELETE FROM desconto;
