package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificacaoMaterialMigrationContractTest {

    @Test
    void migrationCriaOcorrenciaMinimaDaObra() throws IOException {
        String sql = migration("V132__Remodelar_Solicitacoes_Tecnicas_Independentes.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("CREATE TABLE IF NOT EXISTS OCORRENCIAS_OBRAS")
                .contains("ID_OBRA UUID NOT NULL REFERENCES OBRAS(ID)")
                .contains("CODIGO_NORMALIZADO VARCHAR(100) NOT NULL")
                .contains("CONSTRAINT UQ_OCORRENCIAS_OBRAS_OBRA_CODIGO UNIQUE (ID_OBRA, CODIGO_NORMALIZADO)")
                .contains("DROP COLUMN IF EXISTS CLASSIFICACAO_TECNICA")
                .doesNotContain("DROP TABLE MATERIAIS");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
