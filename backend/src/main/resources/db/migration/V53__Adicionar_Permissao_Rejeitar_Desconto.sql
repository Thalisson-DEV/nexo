-- Adicionar permissão faltante para rejeição de descontos
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES 
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:rejeitar');

-- Vincular a permissão recém-criada ao perfil ADMINISTRADOR
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pa.id, p.id
FROM perfis_acesso pa, permissoes p
WHERE pa.nome = 'ADMINISTRADOR'
  AND p.nome = 'desconto:rejeitar';
