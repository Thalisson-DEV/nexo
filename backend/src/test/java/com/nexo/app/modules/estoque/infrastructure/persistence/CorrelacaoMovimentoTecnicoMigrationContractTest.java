package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelacaoMovimentoTecnicoMigrationContractTest {

    @Test
    void migrationAdicionaCorrelacoesNullableEUnicidadeParcialDoEstoqueDaObra() throws IOException {
        String sql = migration("V132__Remodelar_Solicitacoes_Tecnicas_Independentes.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("ALTER TABLE MOVIMENTACOES_ESTOQUE ADD COLUMN IF NOT EXISTS ID_OCORRENCIA UUID")
                .contains("ADD COLUMN IF NOT EXISTS CODIGO_OCORRENCIA_SNAPSHOT VARCHAR(100)")
                .contains("REFERENCES OCORRENCIAS_OBRAS(ID)")
                .contains("DROP COLUMN IF EXISTS ID_AGRUPADOR")
                .contains("UPDATE MOVIMENTACOES_ESTOQUE")
                .doesNotContain("DELETE FROM MOVIMENTACOES_ESTOQUE");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
