package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EstoqueCampoEquipeMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V145__Vincular_Estoque_Campo_Equipes.sql");

    @Test
    void regularizaAusentesPreservaExistentesEDetectaDuplicidade() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertThat(sql)
                .contains("HAVING COUNT(*) > 1")
                .contains("WHERE NOT EXISTS")
                .contains("UPPER(tipo_campo.nome) = 'CAMPO'")
                .contains("UPPER(TRIM(e.cod_equipe))")
                .contains("e.id_base_operacional")
                .contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_estoques_id_equipe")
                .contains("WHERE id_equipe IS NOT NULL");
    }
}
