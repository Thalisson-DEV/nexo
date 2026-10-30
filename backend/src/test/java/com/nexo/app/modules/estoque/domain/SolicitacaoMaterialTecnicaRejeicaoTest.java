package com.nexo.app.modules.estoque.domain;

import com.nexo.app.modules.obras.ObraApi.ObraPublica;
import com.nexo.app.modules.obras.ObraApi.TipoObraPublico;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SolicitacaoMaterialTecnicaRejeicaoTest {

    @Test
    void solicitacaoComDataMasSemEquipeOuReservaPodeSerRejeitada() {
        SolicitacaoMaterial solicitacao = criarSolicitacaoTecnicaPlanejada(false);
        assertThat(EtapaOperacionalSolicitacao.possuiPendenciaDePlanejamento(solicitacao)).isFalse();

        solicitacao.rejeitar(new MotivoRejeicao("CANCELAMENTO", "Projeto cancelado"), "Projeto cancelado");

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.REJEITADO);
    }

    @Test
    void solicitacaoTecnicaPlanejadaPodeSerRejeitada() {
        SolicitacaoMaterial solicitacao = criarSolicitacaoTecnicaPlanejada();
        MotivoRejeicao motivo = new MotivoRejeicao("ERRO DE PROJETO", "Solicitacao nao atende ao projeto");

        solicitacao.rejeitar(motivo, "   Projeto cancelado pelo cliente   ");

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.REJEITADO);
        assertThat(solicitacao.getMotivoRejeicao()).isSameAs(motivo);
        assertThat(solicitacao.getJustificativaRejeicao()).isEqualTo("Projeto cancelado pelo cliente");
        assertThat(solicitacao.getStatusSeparacao()).isNull();
        assertThat(solicitacao.getDataVerificacao()).isNotNull();
    }

    @Test
    void naoDevePermitirRejeitarSolicitacaoTecnicaNaoPlanejada() {
        SolicitacaoMaterial solicitacao = criarSolicitacaoTecnicaNaoPlanejada();
        MotivoRejeicao motivo = new MotivoRejeicao("ERRO DE PROJETO", "Solicitacao nao atende ao projeto");

        assertThatThrownBy(() -> solicitacao.rejeitar(motivo, "Cancelado"))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Apenas solicitações técnicas já planejadas podem ser rejeitadas");
    }

    @Test
    void naoDevePermitirRejeitarSolicitacaoTecnicaComEntregaConfirmada() {
        SolicitacaoMaterial solicitacao = criarSolicitacaoTecnicaPlanejada();
        ItemSolicitacao item = solicitacao.getItens().get(0);
        item.registrarEntrega(new BigDecimal("2.000"), null); // fato operacional
        solicitacao.confirmarEntregaTecnica();

        MotivoRejeicao motivo = new MotivoRejeicao("ERRO DE PROJETO", "Solicitacao nao atende ao projeto");

        assertThatThrownBy(() -> solicitacao.rejeitar(motivo, "Cancelado apos entrega"))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    private SolicitacaoMaterial criarSolicitacaoTecnicaPlanejada() {
        return criarSolicitacaoTecnicaPlanejada(true);
    }

    private SolicitacaoMaterial criarSolicitacaoTecnicaPlanejada(boolean completo) {
        Estoque almoxarifado = mock(Estoque.class);
        when(almoxarifado.getId()).thenReturn(UUID.randomUUID());
        when(almoxarifado.getNomeIdentificador()).thenReturn("ALMOX-CENTRAL");

        Estoque obraEstoque = mock(Estoque.class);
        when(obraEstoque.getId()).thenReturn(UUID.randomUUID());
        when(obraEstoque.getNomeIdentificador()).thenReturn("OBRA-01");

        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra teste", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());

        SolicitacaoMaterial sol = SolicitacaoMaterial.criarTecnica(
                obra, null, TipoSolicitacaoTecnica.ADICAO_COMUM,
                obraEstoque, almoxarifado, completo ? UUID.randomUUID() : null, LocalDate.now().plusDays(5),
                "Observacao da solicitacao tecnica", UUID.randomUUID());

        Material mat = mock(Material.class);
        when(mat.getId()).thenReturn(UUID.randomUUID());
        when(mat.getCodigo()).thenReturn("CABO-01");
        sol.adicionarItemTecnico(mat, new BigDecimal("10.000"));

        ReservaSolicitacaoTecnica reserva = mock(ReservaSolicitacaoTecnica.class);
        if (completo) sol.associarReserva(reserva);
        return sol;
    }

    private SolicitacaoMaterial criarSolicitacaoTecnicaNaoPlanejada() {
        Estoque almoxarifado = mock(Estoque.class);
        when(almoxarifado.getId()).thenReturn(UUID.randomUUID());

        Estoque obraEstoque = mock(Estoque.class);
        when(obraEstoque.getId()).thenReturn(UUID.randomUUID());

        ObraPublica obra = new ObraPublica(
                UUID.randomUUID(), "001", "Obra teste", TipoObraPublico.OBRA, true,
                UUID.randomUUID(), "OBRAS", true, UUID.randomUUID(), Set.of(), null, List.of());

        SolicitacaoMaterial sol = SolicitacaoMaterial.criarTecnica(
                obra, null, TipoSolicitacaoTecnica.ADICAO_COMUM,
                obraEstoque, almoxarifado, null, null,
                null, UUID.randomUUID());

        Material mat = mock(Material.class);
        when(mat.getId()).thenReturn(UUID.randomUUID());
        when(mat.getCodigo()).thenReturn("CABO-01");
        sol.adicionarItemTecnico(mat, new BigDecimal("10.000"));
        return sol;
    }
}
