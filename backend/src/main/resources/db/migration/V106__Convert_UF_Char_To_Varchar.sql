-- V106: Converte coluna uf de CHAR(2) para VARCHAR(2) para evitar falhas de validação de schema no Hibernate.

ALTER TABLE sst_documentos_governanca ALTER COLUMN uf TYPE VARCHAR(2);
