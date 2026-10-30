package com.nexo.app.modules.estoque.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialLookupRepositoryContractTest {

    @Test
    void querySelecionaSomenteCamposDoLookupEAplicaKeysetAtivoEOrdemEstavel() throws Exception {
        Query query = Arrays.stream(MaterialRepository.class.getMethods())
                .filter(method -> method.getName().equals("buscarOpcoes"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Query.class);

        String sql = query.value().toLowerCase();
        assertTrue(sql.contains("m.id as id"));
        assertTrue(sql.contains("m.codigo as codigo"));
        assertTrue(sql.contains("m.descricao as descricao"));
        assertTrue(sql.contains("sortkey"));
        assertTrue(sql.contains("m.ativo = true"));
        assertTrue(sql.contains("m.id > :lastid"));
        assertTrue(sql.contains("order by"));
        assertTrue(sql.contains("m.id"));
        assertTrue(!sql.contains("select m" + ".*"));
    }

    @Test
    void migrationCriaIndicesParaCodigoDescricaoEOrdenacaoDoLookup() throws IOException {
        Path migration = Path.of("src/main/resources/db/migration/V138__Indices_lookup_materiais.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();

        assertTrue(sql.contains("idx_materiais_lookup_codigo_prefixo"));
        assertTrue(sql.contains("idx_materiais_lookup_descricao_prefixo"));
        assertTrue(sql.contains("idx_materiais_lookup_descricao_ordem"));
        assertTrue(sql.contains("text_pattern_ops"));
        assertTrue(!sql.contains("create index concurrently"));
    }
}
