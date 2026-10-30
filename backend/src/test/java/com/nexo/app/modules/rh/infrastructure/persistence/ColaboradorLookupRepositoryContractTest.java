package com.nexo.app.modules.rh.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ColaboradorLookupRepositoryContractTest {

    @Test
    void queriesSelecionamSomenteCamposDoLookupEAplicamAtivoEOrdemDeterministica() {
        Query firstPage = Arrays.stream(ColaboradorRepository.class.getMethods())
                .filter(method -> method.getName().equals("buscarOpcoes"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Query.class);
        Query nextPage = Arrays.stream(ColaboradorRepository.class.getMethods())
                .filter(method -> method.getName().equals("buscarOpcoesAposCursor"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Query.class);

        assertLookupShape(firstPage.value());
        assertLookupShape(nextPage.value());
        assertThat(nextPage.value().toLowerCase())
                .contains("c.nomebusca > :cursorname", "c.id > :cursorid");
    }

    private static void assertLookupShape(String query) {
        String normalized = query.toLowerCase();
        assertThat(normalized)
                .contains("c.id as id", "c.nomecompleto as nomecompleto", "c.nomebusca as nomebusca")
                .contains("c.ativo = true", "c.nomebusca like", "order by c.nomebusca", "c.id")
                .doesNotContain("select c from", "select c.*");
    }
}
