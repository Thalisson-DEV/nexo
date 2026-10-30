package com.nexo.app.modules.obras.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TipoObraMigrationContractTest {

    @Test
    void migrationAdicionaTipoFazBackfillERestringeValoresSemRecriarObras() throws IOException {
        String sql = migration("V115__Adicionar_Tipo_Operacional_Obra.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("ALTER TABLE OBRAS ADD COLUMN TIPO_OBRA VARCHAR(20)")
                .contains("UPDATE OBRAS SET TIPO_OBRA = 'OBRA' WHERE TIPO_OBRA IS NULL")
                .contains("ALTER COLUMN TIPO_OBRA SET DEFAULT 'OBRA'")
                .contains("ALTER COLUMN TIPO_OBRA SET NOT NULL")
                .contains("CHECK (TIPO_OBRA IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT'))")
                .contains("CREATE INDEX IDX_OBRAS_TIPO_OBRA ON OBRAS(TIPO_OBRA)")
                .doesNotContain("DROP TABLE OBRAS")
                .doesNotContain("DELETE FROM OBRAS")
                .doesNotContain("UPDATE OBRAS SET ID");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
