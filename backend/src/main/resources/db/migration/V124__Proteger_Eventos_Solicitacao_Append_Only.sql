-- A trilha de dominio e append-only para toda sessao e todo papel. Correcao
-- extraordinaria deve ser feita por uma migration aditiva auditavel, nunca por
-- uma chave de sessao que a aplicacao possa habilitar.
CREATE FUNCTION bloquear_mutacao_eventos_solicitacao()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '55000',
        MESSAGE = 'eventos_solicitacao_material e append-only';
END;
$$;

CREATE TRIGGER trg_eventos_solicitacao_append_only
BEFORE UPDATE OR DELETE
ON eventos_solicitacao_material
FOR EACH ROW
EXECUTE FUNCTION bloquear_mutacao_eventos_solicitacao();
