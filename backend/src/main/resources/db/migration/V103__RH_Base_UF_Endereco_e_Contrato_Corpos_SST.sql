-- Endereço/UF passam a integrar o cadastro mestre da base. Cadastros legados
-- continuam legíveis com endereço nulo até sua revisão; as quatro bases iniciais
-- têm UF conhecida pelo contexto operacional e recebem BA.
ALTER TABLE bases_operacionais
    ADD COLUMN uf VARCHAR(2),
    ADD COLUMN endereco VARCHAR(300);

UPDATE bases_operacionais
SET uf = 'BA'
WHERE uf IS NULL
  AND UPPER(TRIM(nome)) IN ('BONFIM', 'JACOBINA', 'JUAZEIRO', 'REMANSO');

ALTER TABLE bases_operacionais
    ADD CONSTRAINT ck_base_operacional_uf_brasileira CHECK (
        uf IS NULL OR uf IN (
            'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA',
            'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN',
            'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO'));

-- O formulário e o domínio exigem grau de risco. A migration falha com uma
-- mensagem explícita caso um ambiente já possua ciclo legado incompleto, em vez
-- de inventar um grau para conseguir avançar.
DO $$
DECLARE
    ciclos_sem_grau INTEGER;
BEGIN
    SELECT COUNT(*) INTO ciclos_sem_grau
    FROM corpos_governanca_sst
    WHERE grau_risco IS NULL;

    IF ciclos_sem_grau > 0 THEN
        RAISE EXCEPTION
            'Existem % corpos de governança sem grau de risco; revise-os antes da V103.',
            ciclos_sem_grau;
    END IF;
END $$;

ALTER TABLE corpos_governanca_sst
    ALTER COLUMN grau_risco SET NOT NULL,
    DROP CONSTRAINT ck_corpo_governanca_grau_risco,
    ADD CONSTRAINT ck_corpo_governanca_grau_risco
        CHECK (grau_risco BETWEEN 1 AND 4),
    ADD CONSTRAINT ck_corpo_governanca_uf_brasileira CHECK (
        uf IN (
            'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA',
            'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN',
            'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO'));
