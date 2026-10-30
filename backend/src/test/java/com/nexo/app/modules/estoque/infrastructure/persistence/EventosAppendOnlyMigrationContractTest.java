package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EventosAppendOnlyMigrationContractTest {

    @Test
    void migrationBloqueiaUpdateDeleteSemBloquearInsert() throws IOException {
        String sql = migration("V124__Proteger_Eventos_Solicitacao_Append_Only.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("CREATE FUNCTION BLOQUEAR_MUTACAO_EVENTOS_SOLICITACAO()")
                .contains("ERRCODE = '55000'")
                .contains("BEFORE UPDATE OR DELETE")
                .contains("ON EVENTOS_SOLICITACAO_MATERIAL")
                .contains("FOR EACH ROW")
                .doesNotContain("CURRENT_SETTING")
                .doesNotContain("ALLOW_EVENT_MUTATION")
                .doesNotContain("BEFORE INSERT")
                .doesNotContain("AFTER INSERT");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
