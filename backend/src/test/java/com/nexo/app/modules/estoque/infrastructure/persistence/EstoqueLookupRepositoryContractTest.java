package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EstoqueLookupRepositoryContractTest {

    @Test
    void queryAplicaAtivoAllowlistNormalizacaoKeysetEOrdemDeterministica() {
        Query query = Arrays.stream(EstoqueRepository.class.getMethods())
                .filter(method -> method.getName().equals("buscarOpcoes"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Query.class);

        String jpql = query.value().toLowerCase();
        assertThat(jpql)
                .contains("estoque.ativo = true")
                .contains("tipo.ativo = true")
                .contains("upper(tipo.nome) in :tipos")
                .contains("immutable_unaccent")
                .contains("like concat(:query, '%')")
                .contains("estoque.id > :lastid")
                .contains("order by")
                .contains("estoque.id");
    }

    @Test
    void migrationCriaIndicesParciaisParaNomeETipoAtivos() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V147__Indice_Lookup_Estoques.sql"),
                StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql)
                .contains("idx_estoques_lookup_nome_ativo")
                .contains("immutable_unaccent(lower(nome_identificador)), id")
                .contains("idx_estoques_lookup_tipo_ativo")
                .contains("where ativo = true");
    }
}
