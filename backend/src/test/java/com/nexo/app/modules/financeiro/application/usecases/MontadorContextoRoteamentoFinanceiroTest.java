package com.nexo.app.modules.financeiro.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexo.app.modules.financeiro.domain.CampoCanonico;
import com.nexo.app.modules.financeiro.domain.FormularioSolicitacao;
import com.nexo.app.modules.financeiro.domain.NucleoSolicitacao;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.domain.ValoresCanonicos;
import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoCompraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;

@ExtendWith(MockitoExtension.class)
class MontadorContextoRoteamentoFinanceiroTest {

    private static final UUID SOLICITANTE = UUID.randomUUID();

    @Mock
    private BaseOperacionalApi baseApi;
    @Mock
    private SolicitacaoCompraRepository compraRepository;

    private MontadorContextoRoteamentoFinanceiro montador;

    @BeforeEach
    void preparar() {
        montador = new MontadorContextoRoteamentoFinanceiro(baseApi, compraRepository);
    }

    @Test
    void publicaAsQuatroBasesBaianasNormalizadasSemEscolherDiretor() {
        for (String nome : java.util.List.of("JUAZEIRO", "remanso", "Jacobina", "Bônfim")) {
            UUID baseId = UUID.randomUUID();
            when(baseApi.buscarPorIdResponse(baseId))
                    .thenReturn(Optional.of(base(baseId, nome, "ba")));

            var contexto = montador.montar(compra("FROTA", baseId));

            assertThat(contexto.base()).isIn("JUAZEIRO", "REMANSO", "JACOBINA", "BONFIM");
            assertThat(contexto.uf()).isEqualTo("BA");
            assertThat(contexto.frota()).isTrue();
        }
    }

    @Test
    void distingueBaseBaianaSemFrotaSergipeEOutraBaseDaBahia() {
        UUID juazeiro = UUID.randomUUID();
        UUID aracaju = UUID.randomUUID();
        UUID outraBa = UUID.randomUUID();
        when(baseApi.buscarPorIdResponse(juazeiro))
                .thenReturn(Optional.of(base(juazeiro, "JUAZEIRO", "BA")));
        when(baseApi.buscarPorIdResponse(aracaju))
                .thenReturn(Optional.of(base(aracaju, "ARACAJU", "SE")));
        when(baseApi.buscarPorIdResponse(outraBa))
                .thenReturn(Optional.of(base(outraBa, "OUTRA BASE", "BA")));

        assertThat(montador.montar(compra("PADRAO", juazeiro)).frota()).isFalse();
        assertThat(montador.montar(compra("FROTA", aracaju)))
                .extracting(MontadorContextoRoteamentoFinanceiro.Contexto::base,
                        MontadorContextoRoteamentoFinanceiro.Contexto::uf)
                .containsExactly("ARACAJU", "SE");
        assertThat(montador.montar(compra("FROTA", outraBa)).base()).isEqualTo("OUTRA BASE");
    }

    @Test
    void baseAusentePublicaValoresVaziosEConservaFrota() {
        var contexto = montador.montar(compra("FROTA", null));

        assertThat(contexto.base()).isEmpty();
        assertThat(contexto.uf()).isEmpty();
        assertThat(contexto.frota()).isTrue();
    }

    @Test
    void pagamentoLigadoHerdaFrotaEBaseDaCompra() {
        UUID compraId = UUID.randomUUID();
        UUID baseId = UUID.randomUUID();
        when(compraRepository.findById(compraId))
                .thenReturn(Optional.of(compra("FROTA", baseId)));
        when(baseApi.buscarPorIdResponse(baseId))
                .thenReturn(Optional.of(base(baseId, "REMANSO", "BA")));

        var contexto = montador.montar(pagamento(compraId, null));

        assertThat(contexto).isEqualTo(
                new MontadorContextoRoteamentoFinanceiro.Contexto("REMANSO", "BA", true, false));
    }

    @Test
    void basePropriaDoPagamentoPrevaleceSobreACompra() {
        UUID compraId = UUID.randomUUID();
        UUID baseCompra = UUID.randomUUID();
        UUID basePagamento = UUID.randomUUID();
        when(compraRepository.findById(compraId))
                .thenReturn(Optional.of(compra("FROTA", baseCompra)));
        when(baseApi.buscarPorIdResponse(basePagamento))
                .thenReturn(Optional.of(base(basePagamento, "ARACAJU", "SE")));

        var contexto = montador.montar(pagamento(compraId, basePagamento));

        assertThat(contexto.base()).isEqualTo("ARACAJU");
        assertThat(contexto.uf()).isEqualTo("SE");
        assertThat(contexto.frota()).isTrue();
        assertThat(contexto.obraCivil()).isFalse();
        verify(baseApi).buscarPorIdResponse(basePagamento);
    }

    @Test
    void identificaCompraDeObraCivilPorFormularioOuPorObra() {
        UUID baseId = UUID.randomUUID();
        UUID obraId = UUID.randomUUID();

        var compraFormulario = compra("OBRA_CIVIL", baseId);
        assertThat(montador.montar(compraFormulario).obraCivil()).isTrue();

        var compraComObra = compraComObra("PADRAO", baseId, obraId);
        assertThat(montador.montar(compraComObra).obraCivil()).isTrue();

        var compraPadrao = compra("PADRAO", baseId);
        assertThat(montador.montar(compraPadrao).obraCivil()).isFalse();
    }

    @Test
    void pagamentoHerdaObraCivilDaCompraOuDeObraPropria() {
        UUID compraObraCivilId = UUID.randomUUID();
        UUID compraPadraoId = UUID.randomUUID();
        UUID obraId = UUID.randomUUID();

        when(compraRepository.findById(compraObraCivilId))
                .thenReturn(Optional.of(compra("OBRA_CIVIL", null)));
        when(compraRepository.findById(compraPadraoId))
                .thenReturn(Optional.of(compra("PADRAO", null)));

        var pagtoLigadoObraCivil = pagamento(compraObraCivilId, null);
        assertThat(montador.montar(pagtoLigadoObraCivil).obraCivil()).isTrue();

        var pagtoComObraPropria = pagamentoComObra(compraPadraoId, null, obraId);
        assertThat(montador.montar(pagtoComObraPropria).obraCivil()).isTrue();

        var pagtoPadrao = pagamento(compraPadraoId, null);
        assertThat(montador.montar(pagtoPadrao).obraCivil()).isFalse();
    }

    private static SolicitacaoCompra compra(String codigoFormulario, UUID baseId) {
        FormularioSolicitacao formulario = new FormularioSolicitacao(
                TipoSolicitacao.COMPRA, codigoFormulario, "Compra", null,
                "PADRAO".equals(codigoFormulario));
        return new SolicitacaoCompra(formulario, nucleo(baseId, null), SOLICITANTE);
    }

    private static SolicitacaoCompra compraComObra(String codigoFormulario, UUID baseId, UUID obraId) {
        FormularioSolicitacao formulario = new FormularioSolicitacao(
                TipoSolicitacao.COMPRA, codigoFormulario, "Compra", null,
                "PADRAO".equals(codigoFormulario));
        return new SolicitacaoCompra(formulario, nucleoComObra(baseId, null, obraId), SOLICITANTE);
    }

    private static SolicitacaoPagamento pagamento(UUID compraId, UUID baseId) {
        FormularioSolicitacao formulario = new FormularioSolicitacao(
                TipoSolicitacao.PAGAMENTO, "PADRAO", "Pagamento", null, true);
        return new SolicitacaoPagamento(formulario, nucleo(baseId, compraId), SOLICITANTE);
    }

    private static SolicitacaoPagamento pagamentoComObra(UUID compraId, UUID baseId, UUID obraId) {
        FormularioSolicitacao formulario = new FormularioSolicitacao(
                TipoSolicitacao.PAGAMENTO, "PADRAO", "Pagamento", null, true);
        return new SolicitacaoPagamento(formulario, nucleoComObra(baseId, compraId, obraId), SOLICITANTE);
    }

    private static NucleoSolicitacao nucleo(UUID baseId, UUID compraId) {
        return nucleoComObra(baseId, compraId, null);
    }

    private static NucleoSolicitacao nucleoComObra(UUID baseId, UUID compraId, UUID obraId) {
        Map<CampoCanonico, Object> valores = new HashMap<>();
        valores.put(CampoCanonico.VALOR, BigDecimal.TEN);
        if (baseId != null) {
            valores.put(CampoCanonico.BASE, baseId);
        }
        if (compraId != null) {
            valores.put(CampoCanonico.POSSUI_COMPRA_PREVIA, true);
            valores.put(CampoCanonico.SOLICITACAO_COMPRA, compraId);
        }
        if (obraId != null) {
            valores.put(CampoCanonico.OBRA, obraId);
        }
        return NucleoSolicitacao.de(new ValoresCanonicos(valores));
    }

    private static BaseOperacionalResponse base(UUID id, String nome, String uf) {
        return new BaseOperacionalResponse(id, nome, uf, null, null, null, null, null, true);
    }
}
