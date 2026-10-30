package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class HashesSolicitacoesTecnicasMigrationContractTest {

    @Test
    void migrationConverteTodosOsHashesDaV119ParaVarchar() throws IOException {
        String sql = migration("V128__Converter_Hashes_Solicitacoes_Tecnicas_Para_Varchar.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("ALTER TABLE COMANDOS_IDEMPOTENTES_SOLICITACAO ALTER COLUMN HASH_PAYLOAD TYPE VARCHAR(64)")
                .contains("ALTER TABLE EVENTOS_SOLICITACAO_MATERIAL ALTER COLUMN HASH_ANTERIOR TYPE VARCHAR(64), ALTER COLUMN HASH_ATUAL TYPE VARCHAR(64)")
                .contains("ALTER TABLE LOTES_IMPORTACAO_SOLICITACAO ALTER COLUMN TOKEN_HASH TYPE VARCHAR(64), ALTER COLUMN HASH_ARQUIVO TYPE VARCHAR(64)")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DROP COLUMN");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
