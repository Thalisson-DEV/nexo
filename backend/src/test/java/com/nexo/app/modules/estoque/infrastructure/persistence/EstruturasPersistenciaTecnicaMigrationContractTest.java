package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EstruturasPersistenciaTecnicaMigrationContractTest {

    @Test
    void migrationCriaEstruturasPorSolicitacaoComOcorrenciaEAgenteNullable() throws IOException {
        String sql = migration("V132__Remodelar_Solicitacoes_Tecnicas_Independentes.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("CREATE TABLE IF NOT EXISTS OCORRENCIAS_OBRAS")
                .contains("ALTER COLUMN ID_AGENTE_RESPONSAVEL DROP NOT NULL")
                .contains("ALTER COLUMN NOME_AGENTE_SNAPSHOT DROP NOT NULL")
                .contains("CK_EVENTOS_SOLICITACAO_CADEIA_FINAL")
                .contains("DROP COLUMN IF EXISTS ID_AGRUPADOR")
                .contains("DROP COLUMN IF EXISTS CLASSIFICACAO_TECNICA_SNAPSHOT")
                .contains("DROP TABLE IF EXISTS AGRUPAMENTOS_SOLICITACAO_TECNICA");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
