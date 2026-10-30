package com.nexo.app.modules.financeiro.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FornecedoresFrotaMigrationContractTest {

    private static final String MIGRATION = "V129__Cadastrar_Fornecedores_Frota.sql";

    @Test
    void migrationContemTodaAListaEInsereCadastrosPendentesSemSobrescreverExistentes() throws IOException {
        String sql = migration();
        long totalFornecedoresInformados = Pattern.compile("(?m)^ {4}\\('")
                .matcher(sql)
                .results()
                .count();

        assertThat(totalFornecedoresInformados).isEqualTo(675);
        assertThat(sql)
                .contains("('OFICINA MECÂNICA DO VALE LTDA', '39.790.716/0001-75')")
                .contains("('JUNIOR RADIADORES', NULL)")
                .contains("regexp_replace(documento_informado, '[^0-9]', '', 'g')")
                .contains("'PENDENTE'")
                .contains("WHERE NOT EXISTS")
                .contains("ON CONFLICT (documento) WHERE documento IS NOT NULL DO NOTHING");
    }

    @Test
    void migrationPreservaIncompletosParaCuradoriaEConsolidaDocumentosRepetidos() throws IOException {
        String sql = migration();

        assertThat(sql)
                .contains("documentados AS")
                .contains("GROUP BY documento")
                .contains("Outros nomes informados na importação")
                .contains("sem_documento AS")
                .contains("Documento informado na importação, pendente de correção")
                .contains("CNPJ não informado na importação")
                .contains("documentado.documento IS NOT NULL")
                .doesNotContain("'HOMOLOGADO'")
                .doesNotContain("UPDATE financeiro_fornecedor");
    }

    private static String migration() throws IOException {
        try (var input = new ClassPathResource("db/migration/" + MIGRATION).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
