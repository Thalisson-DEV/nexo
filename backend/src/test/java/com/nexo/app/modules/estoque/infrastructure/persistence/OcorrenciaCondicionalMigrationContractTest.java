package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OcorrenciaCondicionalMigrationContractTest {

    @Test
    void migrationCriaIndicesDasConsultasPorOcorrencia() throws IOException {
        String sql = migration("V132__Remodelar_Solicitacoes_Tecnicas_Independentes.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("CREATE INDEX IF NOT EXISTS IDX_SOLICITACOES_MATERIAL_OBRA_OCORRENCIA_FINAL")
                .contains("CREATE INDEX IF NOT EXISTS IDX_MOVIMENTACOES_ESTOQUE_OBRA_OCORRENCIA_FINAL")
                .contains("CK_SOLICITACOES_MATERIAL_OCORRENCIA_FINAL")
                .contains("CK_MOVIMENTACOES_ESTOQUE_OCORRENCIA_FINAL");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
