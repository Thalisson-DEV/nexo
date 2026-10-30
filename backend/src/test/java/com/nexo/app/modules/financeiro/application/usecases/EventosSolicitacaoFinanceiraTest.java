package com.nexo.app.modules.financeiro.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;

class EventosSolicitacaoFinanceiraTest {

    @Test
    void submissaoPublicaContextoNeutroEMantemSomentePapelSolicitante() {
        UUID solicitante = UUID.randomUUID();
        SolicitacaoCompra compra = new SolicitacaoCompra(
                new FormularioSolicitacao(TipoSolicitacao.COMPRA, "FROTA", "Compra", null, false),
                NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                        CampoCanonico.VALOR, BigDecimal.TEN))),
                solicitante);
        MontadorContextoRoteamentoFinanceiro montador =
                mock(MontadorContextoRoteamentoFinanceiro.class);
        when(montador.montar(compra)).thenReturn(
                new MontadorContextoRoteamentoFinanceiro.Contexto("BONFIM", "BA", true, false));

        var evento = new EventosSolicitacaoFinanceira(montador).submetida(compra);

        assertThat(evento.contexto()).containsEntry("base", "BONFIM")
                .containsEntry("uf", "BA")
                .containsEntry("frota", true)
                .containsEntry("obraCivil", false);
        assertThat(evento.papeis()).isEqualTo(Map.of("solicitante", Set.of(solicitante)));
        assertThat(evento.papeis()).doesNotContainKey("diretor");
    }

    @Test
    void aprovacaoPublicaContextoComObraCivil() {
        UUID solicitante = UUID.randomUUID();
        UUID aprovador = UUID.randomUUID();
        SolicitacaoCompra compra = new SolicitacaoCompra(
                new FormularioSolicitacao(TipoSolicitacao.COMPRA, "OBRA_CIVIL", "Compra Obras Civis", null, false),
                NucleoSolicitacao.de(new ValoresCanonicos(Map.of(
                        CampoCanonico.VALOR, BigDecimal.TEN))),
                solicitante);
        MontadorContextoRoteamentoFinanceiro montador =
                mock(MontadorContextoRoteamentoFinanceiro.class);
        when(montador.montar(compra)).thenReturn(
                new MontadorContextoRoteamentoFinanceiro.Contexto("", "", false, true));

        compra.submeter();
        compra.aprovar(aprovador, BigDecimal.TEN, "Aprovado engenharia");

        var evento = new EventosSolicitacaoFinanceira(montador).aprovada(compra);

        assertThat(evento.contexto()).containsEntry("obraCivil", true);
        assertThat(evento.papeis()).isEqualTo(Map.of("solicitante", Set.of(solicitante)));
    }
}
