-- V84.1: desarma a colisão de pg_temp entre a V58 e a V85.
--
-- O Flyway aplica todas as migrations pendentes numa única conexão, e função
-- criada em pg_temp vive enquanto a sessão viver. A V58 e a V85 criam, cada uma,
-- uma pg_temp.f_form com a mesma assinatura. Isso é inofensivo quando as duas
-- entram em deploys diferentes — foi o que aconteceu em todos os bancos que
-- existem hoje —, mas é fatal num banco criado do zero: ali as duas correm na
-- mesma sessão e a V85 morre com «function "f_form" already exists».
--
-- Correr entre as duas resolve sem reescrever migration já aplicada, que exigiria
-- flyway repair em produção. Em banco que já passou da V85 isto é um no-op:
-- spring.flyway.out-of-order=true a aplica fora de ordem e o IF EXISTS não
-- encontra nada (a sessão do deploy não tem schema temporário).
--
-- Migration nova não deve criar helper em pg_temp sem removê-lo ao fim.

DROP FUNCTION IF EXISTS pg_temp.f_form(TEXT, TEXT, TEXT, TEXT, BOOLEAN);
