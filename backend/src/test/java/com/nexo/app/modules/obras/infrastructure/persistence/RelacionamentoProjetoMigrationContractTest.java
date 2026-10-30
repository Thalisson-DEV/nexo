package com.nexo.app.modules.obras.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RelacionamentoProjetoMigrationContractTest {

    @Test
    void migrationCriaParCanonicoConsultavelPelosDoisProjetosEPreservaAliasesLegados() throws IOException {
        String sql = migration("V116__Criar_Relacionamentos_Projetos.sql");
        String normalizado = sql.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertThat(normalizado)
                .contains("CREATE TABLE OBRAS_RELACIONAMENTOS")
                .contains("ID_PROJETO_A UUID NOT NULL REFERENCES OBRAS(ID)")
                .contains("ID_PROJETO_B UUID NOT NULL REFERENCES OBRAS(ID)")
                .contains("CHECK (ID_PROJETO_A::TEXT < ID_PROJETO_B::TEXT)")
                .contains("CHECK (TIPO IN ('KIT', 'MEDIDOR'))")
                .contains("UNIQUE (ID_PROJETO_A, ID_PROJETO_B)")
                .contains("ON OBRAS_RELACIONAMENTOS(ID_PROJETO_A)")
                .contains("ON OBRAS_RELACIONAMENTOS(ID_PROJETO_B)")
                .contains("FROM OBRAS OBRA WHERE OBRA.ID_OBRA_PAI IS NOT NULL")
                .contains("ON CONFLICT (ID_PROJETO_A, ID_PROJETO_B) DO NOTHING")
                .doesNotContain("DROP COLUMN ID_OBRA_PAI")
                .doesNotContain("DROP COLUMN TIPO_VINCULO_PAI")
                .doesNotContain("DELETE FROM OBRAS");
    }

    private static String migration(String nome) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
