-- Recupera a base das inspeções históricas quando o inspetor possui
-- um colaborador vinculado. Inspeções sem vínculo conhecido permanecem
-- sem base para preservar os dados históricos sem inferência incorreta.
UPDATE inspecoes_seguranca inspecao
SET id_base_operacional = colaborador.id_base_operacional
FROM colaboradores colaborador
WHERE colaborador.id_usuario = inspecao.id_inspetor
  AND inspecao.id_base_operacional IS NULL;
