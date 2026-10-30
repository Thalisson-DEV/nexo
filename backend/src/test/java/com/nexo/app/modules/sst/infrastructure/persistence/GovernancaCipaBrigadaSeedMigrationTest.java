package com.nexo.app.modules.sst.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancaCipaBrigadaSeedMigrationTest {

    @Test
    void conteudoInicialFicaRestritoAosDoisCorpos() throws Exception {
        String sql = migration("V101__SST_Governanca_Conteudo_Inicial_CIPA_Brigada.sql");

        assertTrue(sql.contains("CIPA_FORMACAO_MEMBROS"));
        assertTrue(sql.contains("BRIGADA_FORMACAO"));
        assertTrue(sql.contains("AND id_arquivo_docx IS NULL"));
        assertTrue(sql.contains("SET status = 'RASCUNHO'"));
        assertTrue(sql.contains("Modelo inicial — Luzes de emergência e tomadas"));
        assertTrue(sql.contains("Modelo inicial — Extintores de incêndio"));
        assertFalse(sql.contains("instituicoes_treinamento_sst"));
        assertFalse(sql.contains("referencias_matriz_treinamento_sst"));
        assertFalse(sql.contains("processos_treinamento_sst"));
    }

    @Test
    void inventarioDeJuazeiroPreservaQuantidadeOrigemERascunho() throws Exception {
        String sql = migration("V102__SST_Governanca_Ativos_Juazeiro.sql");

        assertEquals(22, ocorrencias(sql, "(?m)^    \\(\\d{1,2}, '[^']+'\\),?$"));
        assertEquals(14, ocorrencias(sql,
                "(?m)^    \\(\\d{1,2}, '(?:PQS|AP)', '[^']+', '[^']+', '[^']+'\\),?$"));
        assertTrue(sql.contains("'RASCUNHO'"));
        assertTrue(sql.contains("v.posto = 10 AND v.tipo_extintor = '8'"));
        assertTrue(sql.contains(
                "a929440d8bbd19bc7803ce50404c76621f84db53be51bbc917d426f39b620b71"));
        assertTrue(sql.contains("UPPER(TRIM(nome)) = 'JUAZEIRO' AND ativo"));
    }

    @Test
    void contratoDeBaseECorpoRestringeUfEGrauDeRisco() throws Exception {
        String sql = migration("V103__RH_Base_UF_Endereco_e_Contrato_Corpos_SST.sql");

        assertTrue(sql.contains("ADD COLUMN uf VARCHAR(2)"));
        assertTrue(sql.contains("ADD COLUMN endereco VARCHAR(300)"));
        assertTrue(sql.contains("'BONFIM', 'JACOBINA', 'JUAZEIRO', 'REMANSO'"));
        assertTrue(sql.contains("ALTER COLUMN grau_risco SET NOT NULL"));
        assertTrue(sql.contains("ck_base_operacional_uf_brasileira"));
        assertTrue(sql.contains("ck_corpo_governanca_uf_brasileira"));
        assertEquals(2, ocorrencias(sql, "'AC', 'AL', 'AP', 'AM', 'BA'"));
    }

    private static int ocorrencias(String texto, String regex) {
        return (int) Pattern.compile(regex).matcher(texto).results().count();
    }

    private static String migration(String nome) throws Exception {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
