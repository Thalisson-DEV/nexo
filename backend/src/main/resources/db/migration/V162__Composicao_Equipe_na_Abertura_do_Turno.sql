-- Na abertura do turno o responsável da equipe confirma (ou corrige) o veículo e os
-- membros. O turno guarda o retrato dessa composição para rastreabilidade das
-- execuções lançadas nele, independentemente de alterações posteriores na equipe.
ALTER TABLE turnos
    ADD COLUMN id_veiculo UUID;

CREATE TABLE turno_membros (
    id_turno UUID NOT NULL REFERENCES turnos(id),
    id_colaborador UUID NOT NULL,
    PRIMARY KEY (id_turno, id_colaborador)
);

CREATE INDEX ix_turno_membros_colaborador ON turno_membros(id_colaborador);
