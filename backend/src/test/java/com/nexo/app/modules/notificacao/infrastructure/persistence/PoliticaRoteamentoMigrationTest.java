package com.nexo.app.modules.notificacao.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PoliticaRoteamentoMigrationTest {

    @Test
    void criaModeloComFallbackUnicoCascatasIndicesENovoTipo() throws Exception {
        String sql = migration("V127__Politicas_Reutilizaveis_Roteamento_Notificacao.sql");

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS politicas_roteamento_notificacao")
                .contains("CREATE TABLE IF NOT EXISTS rotas_roteamento_notificacao")
                .contains("CREATE TABLE IF NOT EXISTS condicoes_rota_roteamento_notificacao")
                .contains("ON DELETE CASCADE")
                .contains("uq_rotas_roteamento_padrao")
                .contains("WHERE padrao = TRUE AND ativo = TRUE")
                .contains("uq_rotas_roteamento_ordem")
                .contains("idx_condicoes_rota_roteamento")
                .contains("'PAPEL_DINAMICO', 'POLITICA'")
                .contains("tipo IN ('PERFIL', 'USUARIO', 'POLITICA')");
    }

    @Test
    void naoGravaIdentidadesAmbientaisNemPapelDiretor() throws Exception {
        String sql = migration("V127__Politicas_Reutilizaveis_Roteamento_Notificacao.sql");

        assertThat(sql.toUpperCase())
                .doesNotContain("FELIPE")
                .doesNotContain("CLAUDIO")
                .doesNotContain("CLÁUDIO")
                .doesNotContain("FPORDEUS")
                .doesNotContain("CCAMPOS")
                .doesNotContain("'DIRETOR'");
    }

    @Test
    void bootstrapsDeUsuariosDeClienteNaoTemEfeitoNaDistribuicao() throws Exception {
        // Os roteamentos de diretoria (V130) e de obras civis (V159) eram de um cliente
        // específico; as versões ficam reservadas, sem pessoas nem logins.
        for (String nome : new String[] {
                "V130__Configurar_Roteamento_Diretoria_Financeira.sql",
                "V159__Configurar_Roteamento_Obras_Civis.sql"}) {
            assertThat(migration(nome).toUpperCase())
                    .contains("SELECT 1;")
                    .doesNotContain("FROM USUARIOS")
                    .doesNotContain("FELIPE")
                    .doesNotContain("CLAUDIO")
                    .doesNotContain("LUCIANO");
        }
    }

    private static String migration(String nome) throws Exception {
        try (var input = new ClassPathResource("db/migration/" + nome).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
