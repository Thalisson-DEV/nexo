package com.nexo.app.modules.estoque.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubEtapaKanbanTest {

    @Test
    @DisplayName("Solicitação rejeitada calcula como REJEITADO no macro RECUSADO")
    void calculaRejeitado() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.REJEITADO);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.REJEITADO);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.RECUSADO);
    }

    @Test
    @DisplayName("Solicitação cancelada calcula como CANCELADO no macro RECUSADO")
    void calculaCancelado() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.CANCELADO);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.CANCELADO);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.RECUSADO);
    }

    @Test
    @DisplayName("Solicitação entregue calcula como CONCLUIDO no macro ENTREGA")
    void calculaConcluido() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.CONCLUIDO);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.ENTREGA);
    }

    @Test
    @DisplayName("Solicitação em separação calcula como EM_SEPARACAO no macro SEPARACAO")
    void calculaEmSeparacao() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.APROVADO);
        when(sol.getStatusSeparacao()).thenReturn(StatusSeparacao.EM_SEPARACAO);
        when(sol.getTipoTecnico()).thenReturn(TipoSolicitacaoTecnica.ADICAO_COMUM);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.EM_SEPARACAO);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.SEPARACAO);
    }

    @Test
    @DisplayName("Solicitação sem data prevista calcula como PENDENTE_PLANEJAMENTO no macro PLANEJAMENTO")
    void calculaPendentePlanejamento() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.APROVADO);
        when(sol.getDataPrevistaRetirada()).thenReturn(null);
        when(sol.getTipoTecnico()).thenReturn(TipoSolicitacaoTecnica.ADICAO_COMUM);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.PENDENTE_PLANEJAMENTO);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.PLANEJAMENTO);
    }

    @Test
    @DisplayName("Solicitação comercial pendente calcula como PENDENTE_APROVACAO no macro PLANEJAMENTO")
    void calculaComercialPendenteAprovacao() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.PENDENTE);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.PENDENTE_APROVACAO);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.PLANEJAMENTO);
    }

    @Test
    @DisplayName("Solicitação comercial aprovada calcula como PENDENTE_RETIRADA no macro ENTREGA")
    void calculaComercialAprovadaPendenteRetirada() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.APROVADO);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.PENDENTE_RETIRADA);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.ENTREGA);
    }

    @Test
    @DisplayName("Solicitação comercial parcialmente entregue calcula como PENDENTE_RETIRADA no macro ENTREGA")
    void calculaComercialPendenteRetirada() {
        SolicitacaoMaterial sol = mock(SolicitacaoMaterial.class);
        when(sol.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(sol.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);

        SubEtapaKanban sub = SubEtapaKanban.calcular(sol);
        assertThat(sub).isEqualTo(SubEtapaKanban.PENDENTE_RETIRADA);
        assertThat(sub.getMacroEtapa()).isEqualTo(MacroEtapaKanban.ENTREGA);
    }
}
