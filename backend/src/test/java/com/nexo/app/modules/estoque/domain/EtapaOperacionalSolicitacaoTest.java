package com.nexo.app.modules.estoque.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EtapaOperacionalSolicitacaoTest {

    @Test
    void aprovadaSemDataFicaAguardandoPlanejamento() {
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                StatusSeparacao.AGUARDANDO_AGENDAMENTO,
                null, UUID.randomUUID(), mock(ReservaSolicitacaoTecnica.class));

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.AGUARDANDO_PLANEJAMENTO);
        assertThat(EtapaOperacionalSolicitacao.possuiPendenciaDePlanejamento(solicitacao)).isTrue();
    }

    @Test
    void adicaoComDataPrevistaSemEquipeOuReservaFicaProntaParaSeparacao() {
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                StatusSeparacao.PRONTO_PARA_SEPARACAO,
                LocalDate.now(), null, null);

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.PRONTO_PARA_SEPARACAO);
        assertThat(EtapaOperacionalSolicitacao.possuiPendenciaDePlanejamento(solicitacao)).isFalse();
    }

    @Test
    void adicaoEmSeparacaoFicaNaEtapaCorrespondente() {
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                StatusSeparacao.EM_SEPARACAO,
                LocalDate.now(), UUID.randomUUID(), mock(ReservaSolicitacaoTecnica.class));

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.EM_SEPARACAO);
    }

    @Test
    void adicaoSeparadaComSaldoPendenteFicaPendenteDeEntrega() {
        ItemSolicitacao item = mock(ItemSolicitacao.class);
        when(item.getQuantidadePendente()).thenReturn(new BigDecimal("1.000"));
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL,
                StatusSeparacao.SEPARADO,
                LocalDate.now(), UUID.randomUUID(), mock(ReservaSolicitacaoTecnica.class));
        when(solicitacao.getItens()).thenReturn(List.of(item));

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.PENDENTE_ENTREGA);
    }

    @Test
    void devolucaoComStatusSeparacaoNuloEPlanejamentoCompletoFicaPendenteDevolucao() {
        ItemSolicitacao item = mock(ItemSolicitacao.class);
        when(item.getQuantidadePendente()).thenReturn(new BigDecimal("1.000"));
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.DEVOLUCAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                null,
                LocalDate.now(), UUID.randomUUID(), mock(ReservaSolicitacaoTecnica.class));
        when(solicitacao.getItens()).thenReturn(List.of(item));

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.PENDENTE_DEVOLUCAO);
    }

    @Test
    void devolucaoSemPlanejamentoFicaPendenteDevolucao() {
        ItemSolicitacao item = mock(ItemSolicitacao.class);
        when(item.getQuantidadePendente()).thenReturn(new BigDecimal("1.000"));
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.DEVOLUCAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                null,
                null, null, null);
        when(solicitacao.getItens()).thenReturn(List.of(item));

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.PENDENTE_DEVOLUCAO);
    }

    @Test
    void solicitacaoEncerradaNaoFicaEmFilaAberta() {
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.APROVADO,
                StatusSeparacao.SEPARADO,
                LocalDate.now(), UUID.randomUUID(), mock(ReservaSolicitacaoTecnica.class));
        when(solicitacao.estaEncerrada()).thenReturn(true);

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.CONCLUIDA);
    }

    @Test
    void solicitacaoRejeitadaFicaConcluida() {
        SolicitacaoMaterial solicitacao = tecnica(
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                SolicitacaoMaterial.StatusSolicitacao.REJEITADO,
                null,
                LocalDate.now(), UUID.randomUUID(), mock(ReservaSolicitacaoTecnica.class));

        assertThat(EtapaOperacionalSolicitacao.calcular(solicitacao))
                .isEqualTo(EtapaOperacionalSolicitacao.CONCLUIDA);
    }

    private SolicitacaoMaterial tecnica(
            TipoSolicitacaoTecnica tipo,
            SolicitacaoMaterial.StatusSolicitacao status,
            StatusSeparacao statusSeparacao,
            LocalDate data,
            UUID idEquipe,
            ReservaSolicitacaoTecnica reserva
    ) {
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacao.getContexto()).thenReturn(ContextoSolicitacaoMaterial.TECNICO);
        when(solicitacao.getTipoTecnico()).thenReturn(tipo);
        when(solicitacao.getStatus()).thenReturn(status);
        when(solicitacao.getStatusSeparacao()).thenReturn(statusSeparacao);
        when(solicitacao.getDataPrevistaRetirada()).thenReturn(data);
        when(solicitacao.getIdEquipeSaque()).thenReturn(idEquipe);
        when(solicitacao.getReserva()).thenReturn(reserva);
        when(solicitacao.isSaqueForaEquipe()).thenReturn(false);
        when(solicitacao.estaEncerrada()).thenReturn(false);
        when(solicitacao.getItens()).thenReturn(List.of());
        return solicitacao;
    }
}
