-- Descarta a integração de telemetria de frota (Vivo Frota Inteligente).
-- O módulo `frota` foi removido do backend: a integração estava quebrada e nenhum
-- fluxo do Nexo lê ou escreve estes eventos. A telemetria técnica do app de campo
-- (tabela telemetria_mobile, V166) é outra coisa e não é afetada.
--
-- Destrutiva: os eventos históricos recebidos da Vivo são apagados de forma definitiva.
-- A view do Copiloto depende da tabela e sai primeiro.

DROP VIEW IF EXISTS copiloto.telemetria_eventos;

DROP TABLE IF EXISTS telemetria_eventos;
