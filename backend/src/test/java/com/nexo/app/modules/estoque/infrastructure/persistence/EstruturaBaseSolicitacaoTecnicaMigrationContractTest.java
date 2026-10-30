package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EstruturaBaseSolicitacaoTecnicaMigrationContractTest {

    @Test
    void migrationAdicionaSolicitacaoIndependenteEReferenciaDeOcorrencia() throws IOException {
        String sql = migration("V132__Remodelar_Solicitacoes_Tecnicas_Independentes.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("ALTER TABLE SOLICITACOES_MATERIAL ADD COLUMN IF NOT EXISTS ID_OCORRENCIA UUID")
                .contains("ADD COLUMN IF NOT EXISTS CODIGO_OCORRENCIA_SNAPSHOT VARCHAR(100)")
                .contains("CK_SOLICITACOES_MATERIAL_TECNICA_FINAL")
                .contains("DROP COLUMN IF EXISTS ID_AGRUPADOR")
                .contains("DROP COLUMN IF EXISTS DIRECAO")
                .contains("DROP COLUMN IF EXISTS CLASSIFICACAO_TECNICA_SNAPSHOT")
                .contains("DROP COLUMN IF EXISTS OCORRENCIA")
                .doesNotContain("DROP TABLE SOLICITACOES_MATERIAL");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
