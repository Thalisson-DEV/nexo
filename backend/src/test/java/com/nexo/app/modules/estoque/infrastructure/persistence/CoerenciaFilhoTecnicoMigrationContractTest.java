package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CoerenciaFilhoTecnicoMigrationContractTest {

    @Test
    void migrationExigeTipoEOcorrenciaCoerentesParaSolicitacaoTecnica() throws IOException {
        String sql = migration("V132__Remodelar_Solicitacoes_Tecnicas_Independentes.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("CK_SOLICITACOES_MATERIAL_OCORRENCIA_FINAL")
                .contains("TIPO_TECNICO IN ('221', '921', '222', '922')")
                .contains("ID_OCORRENCIA IS NOT NULL")
                .contains("CODIGO_OCORRENCIA_SNAPSHOT IS NOT NULL")
                .contains("DROP COLUMN IF EXISTS ID_AGRUPADOR")
                .doesNotContain("CREATE FUNCTION VALIDAR_COERENCIA");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
