-- Mantem V109 imutavel e aplica, de forma evolutiva, a regra introduzida depois
-- de sua execucao inicial. A validacao evita truncamento silencioso de dados.
DO $$
DECLARE
    quantidade_invalidos BIGINT;
BEGIN
    SELECT COUNT(*)
      INTO quantidade_invalidos
      FROM obras
     WHERE LENGTH(numero) > 7;

    IF quantidade_invalidos > 0 THEN
        RAISE EXCEPTION
            'Nao foi possivel limitar obras.numero a 7 caracteres: % registro(s) excedem o limite.',
            quantidade_invalidos
            USING ERRCODE = '22001';
    END IF;
END
$$;

ALTER TABLE obras
    ALTER COLUMN numero TYPE VARCHAR(7);
