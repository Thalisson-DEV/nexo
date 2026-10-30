package com.nexo.app.modules.assistente.application.semantic;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SqlGuardTest {

    private final SqlGuard guard = new SqlGuard(new SemanticCatalog(mock(JdbcTemplate.class)));
    private final SemanticQueryContext almoxarife = new SemanticQueryContext(UUID.randomUUID(),
            Set.of("estoque:movimentacao:read", "estoque:material:read", "estoque:saldo:read"));

    @Test
    void aceitaSelectComJuncaoCteEJanelaSobreViewsAutorizadas() {
        String sql = """
                WITH saidas AS (
                  SELECT m.material_id, date_trunc('month', m.data) AS mes, sum(m.quantidade) AS total
                  FROM copiloto.movimentacoes m
                  WHERE m.tipo_movimentacao IN ('ENTREGA', 'APLICACAO')
                  GROUP BY 1, 2
                )
                SELECT s.mes, mat.descricao, s.total,
                       rank() OVER (PARTITION BY s.mes ORDER BY s.total DESC) AS posicao
                FROM saidas s JOIN copiloto.materiais mat ON mat.material_id = s.material_id;
                """;
        SqlGuard.ValidatedSql validado = guard.validar(sql, almoxarife);
        assertThat(validado.views()).containsExactlyInAnyOrder("movimentacoes", "materiais");
        assertThat(validado.sql()).doesNotEndWith(";");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM copiloto.movimentacoes",
            "UPDATE copiloto.materiais SET descricao = 'x'",
            "SELECT * FROM copiloto.movimentacoes; DROP TABLE usuarios",
            "SELECT * INTO copia FROM copiloto.movimentacoes",
            "SELECT * FROM copiloto.movimentacoes FOR UPDATE",
            "SELECT pg_sleep(10) FROM copiloto.movimentacoes",
            "SELECT set_config('copiloto.usuario_id', 'x', true) FROM copiloto.movimentacoes",
            "SELECT current_setting('copiloto.usuario_id') FROM copiloto.movimentacoes",
            "SELECT * FROM usuarios",
            "SELECT * FROM public.usuarios",
            "SELECT * FROM pg_catalog.pg_tables",
            "SELECT * FROM copiloto.nao_existe",
            "SELECT 1",
            "WITH x AS (DELETE FROM copiloto.movimentacoes RETURNING *) SELECT * FROM x",
            "COPY (SELECT * FROM copiloto.movimentacoes) TO '/tmp/x'",
            "SELECT dblink_exec('x') FROM copiloto.movimentacoes"
    })
    void rejeitaEscritaMultiplosComandosFuncoesDeSistemaETabelasForaDaCamada(String sql) {
        assertThatThrownBy(() -> guard.validar(sql, almoxarife)).isInstanceOf(SqlGuard.SqlRejeitadoException.class);
    }

    @Test
    void palavrasProibidasDentroDeLiteraisEComentariosNaoBloqueiam() {
        String sql = "SELECT descricao FROM copiloto.materiais -- DELETE aqui e comentario\n"
                + "WHERE descricao ILIKE '%update; drop%'";
        assertThat(guard.validar(sql, almoxarife).views()).containsExactly("materiais");
    }

    @Test
    void negaViewSemPermissaoDoUsuario() {
        SemanticQueryContext semFinanceiro = new SemanticQueryContext(UUID.randomUUID(), Set.of("estoque:material:read"));
        assertThatThrownBy(() -> guard.validar("SELECT * FROM copiloto.solicitacoes_pagamento", semFinanceiro))
                .isInstanceOf(SqlGuard.SqlAcessoNegadoException.class);
    }

    @Test
    void viewsPublicasParaQualquerAutenticado() {
        SemanticQueryContext qualquer = new SemanticQueryContext(UUID.randomUUID(), Set.of());
        assertThat(guard.validar("SELECT count(*) FROM copiloto.comunicados", qualquer).views()).containsExactly("comunicados");
    }
}
