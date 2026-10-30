package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaHistoricoMigrationContractTest {

    @Test
    void migrationsPreservamJsonCanonicoEVersionamFormatoTemporalDaAssinatura() throws IOException {
        String payload = normalizar(migration("V133__Preservar_Payload_Canonico_Historico.sql"));
        String assinatura = normalizar(migration("V134__Versionar_Assinatura_Historico.sql"));

        assertThat(payload)
                .contains("ALTER COLUMN PAYLOAD_CANONICO TYPE TEXT")
                .contains("CHECK (PAYLOAD_CANONICO IS JSON)");
        assertThat(assinatura)
                .contains("ADD COLUMN VERSAO_FORMATO_HASH INTEGER NOT NULL DEFAULT 1")
                .contains("ALTER COLUMN OCORRIDO_EM TYPE TIMESTAMP(6) WITH TIME ZONE");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String normalizar(String sql) {
        return sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
