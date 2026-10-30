package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class BackfillSolicitacoesComerciaisMigrationContractTest {

    @Test
    void migrationFazBackfillSemInventarIntegridadeOuFatosOperacionais() throws IOException {
        String sql = migration("V123__Backfill_Solicitacoes_Comerciais_Legadas.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("UPDATE SOLICITACOES_MATERIAL SET CONTEXTO = 'COMERCIAL' WHERE CONTEXTO IS NULL")
                .contains("ALTER COLUMN CONTEXTO SET NOT NULL")
                .contains("WHEN STATUS = 'APROVADO' THEN 'PRONTO_PARA_SEPARACAO'")
                .contains("WHEN STATUS IN ('ENTREGUE_PARCIAL', 'ENTREGUE', 'ESTORNADO') THEN 'SEPARADO'")
                .contains("QUANTIDADE_ENTREGUE_BRUTA = COALESCE(ITEM.QUANTIDADE_ENTREGUE, 0)")
                .contains("WHEN SOLICITACAO.STATUS = 'ESTORNADO' THEN COALESCE(ITEM.QUANTIDADE_ENTREGUE, 0)")
                .contains("'LEGACY_BASELINE'")
                .contains("'INTEGRIDADE', 'NAO_VERIFICADO'")
                .contains("HASH_ANTERIOR")
                .contains("HASH_ATUAL")
                .contains("VERSAO_CHAVE")
                .doesNotContain("DELETE FROM SOLICITACOES_MATERIAL")
                .doesNotContain("DROP TABLE");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
