package com.nexo.app.modules.sst.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FormularioBlitzGravidadeMigrationContractTest {

    private static final String MIGRATION =
            "db/migration/V137__SST_Classificar_Gravidades_Formulario_Blitz_Geral.sql";
    private static final Pattern ITEM = Pattern.compile(
            "(?m)^\\s*\\('([0-9a-f-]{36})', '([0-9a-f-]{36})', '(BAIXA|MEDIA|ALTA|CRITICA)'\\)[,;] -- \\d+$");

    @Test
    void mapeiaTodasAsPerguntasNaOrdemFuncionalInformada() throws IOException {
        List<MatchResult> itens = ITEM.matcher(migration()).results().toList();

        assertThat(itens).hasSize(54);
        assertThat(itens.stream().map(item -> item.group(1))).doesNotHaveDuplicates();
        assertThat(itens.stream().map(item -> item.group(2))).doesNotHaveDuplicates();
        assertThat(itens.stream().map(item -> item.group(3))).containsExactly(
                "CRITICA", "CRITICA", "CRITICA", "ALTA", "CRITICA", "ALTA", "ALTA", "ALTA", "ALTA",
                "ALTA", "ALTA", "ALTA", "ALTA", "MEDIA", "ALTA", "ALTA", "ALTA", "CRITICA", "CRITICA",
                "CRITICA", "ALTA", "CRITICA", "CRITICA", "CRITICA", "ALTA", "ALTA", "MEDIA", "MEDIA",
                "MEDIA", "CRITICA", "ALTA", "MEDIA", "BAIXA", "MEDIA", "MEDIA", "MEDIA", "ALTA", "ALTA",
                "ALTA", "ALTA", "MEDIA", "MEDIA", "MEDIA", "CRITICA", "MEDIA", "MEDIA", "CRITICA", "ALTA",
                "CRITICA", "MEDIA", "ALTA", "ALTA", "MEDIA", "MEDIA");
    }

    @Test
    void validaEstruturaAntesDeAtualizarEPreservaDesviosHistoricos() throws IOException {
        String sql = migration();

        assertThat(sql)
                .contains("v_formulario_id CONSTANT UUID := '98e41822-9276-496a-967b-9b46e1f70130'")
                .contains("IF NOT EXISTS (")
                .contains("RETURN;")
                .contains("opcao.id_pergunta = mapeamento.id_pergunta")
                .contains("grupo.id_formulario = v_formulario_id")
                .contains("opcao.gera_desvio = TRUE")
                .contains("IF v_total_opcoes_desvio <> v_total_esperado THEN")
                .contains("pergunta.tipo_resposta = 'MULTIPLA_ESCOLHA'")
                .contains("IF v_total_valido <> v_total_esperado THEN")
                .contains("UPDATE opcoes_resposta opcao")
                .contains("opcao.gravidade IS DISTINCT FROM mapeamento.gravidade")
                .contains("usuario_ultima_alteracao = 'migration:V137'")
                .contains("IF v_total_classificado <> v_total_esperado THEN")
                .doesNotContain("UPDATE desvios_seguranca");
    }

    private static String migration() throws IOException {
        try (var input = new ClassPathResource(MIGRATION).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
