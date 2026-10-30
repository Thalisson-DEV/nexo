-- V136: Garante que a trigger append-only de eventos_solicitacao_material esteja ativa.
--
-- Necessario para reativar a trigger caso a V131_1 tenha sido aplicada out-of-order
-- em ambientes onde a V132 ja havia sido executada previamente.

DROP TRIGGER IF EXISTS trg_eventos_solicitacao_append_only ON eventos_solicitacao_material;

CREATE TRIGGER trg_eventos_solicitacao_append_only
BEFORE UPDATE OR DELETE ON eventos_solicitacao_material
FOR EACH ROW EXECUTE FUNCTION bloquear_mutacao_eventos_solicitacao();
