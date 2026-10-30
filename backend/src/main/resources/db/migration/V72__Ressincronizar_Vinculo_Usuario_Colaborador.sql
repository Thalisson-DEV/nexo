-- Repete o saneamento da V61 para os vínculos criados depois dela.
--
-- Até agora, criar um usuário direto pelo CRUD do IAM gravava apenas
-- usuarios.id_colaborador; o caminho inverso (colaboradores.id_usuario) só era
-- preenchido pelo fluxo de "solicitar acesso" a partir do RH e pela importação em massa.
-- Colaboradores nessa situação aparecem sem usuário para o resto do sistema — no
-- acompanhamento de metas de inspeção, por exemplo, o realizado deles fica sempre zerado.
--
-- A origem foi corrigida no UsuarioUseCases#criar, que agora publica
-- UsuarioCriadoParaColaboradorEvent. Esta migration acerta o passivo.

WITH usuario_canonico AS (
    -- colaboradores.id_usuario é UNIQUE: em caso de mais de um usuário apontando para o
    -- mesmo colaborador, elege o ativo mais recente e ignora os demais.
    SELECT DISTINCT ON (id_colaborador)
           id_colaborador,
           id AS id_usuario
    FROM usuarios
    WHERE id_colaborador IS NOT NULL
    ORDER BY id_colaborador, ativo DESC, data_criacao DESC
)
UPDATE colaboradores colaborador
SET id_usuario = usuario_canonico.id_usuario,
    versao = COALESCE(colaborador.versao, 0) + 1,
    data_ultima_alteracao = CURRENT_TIMESTAMP,
    usuario_ultima_alteracao = 'SISTEMA'
FROM usuario_canonico
WHERE usuario_canonico.id_colaborador = colaborador.id
  AND colaborador.id_usuario IS NULL
  -- não rouba um usuário já vinculado a outro colaborador
  AND NOT EXISTS (
      SELECT 1
      FROM colaboradores outro
      WHERE outro.id_usuario = usuario_canonico.id_usuario
  );
