package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.AcaoPermitidaSolicitacao;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PoliticaAcoesPermitidasSolicitacaoTest {

    private final PoliticaAcoesPermitidasSolicitacao politica = new PoliticaAcoesPermitidasSolicitacao();

    @AfterEach
    void limparSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void devolucaoNuncaExplicaSeparacao() {
        autenticar("estoque:solicitacao:separate");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, null);

        Set<AcaoPermitidaSolicitacao> acoes = politica.calcular(solicitacao);

        assertThat(acoes).doesNotContain(
                AcaoPermitidaSolicitacao.INICIAR_SEPARACAO,
                AcaoPermitidaSolicitacao.CONCLUIR_SEPARACAO);
    }

    @Test
    void adicaoSeparadaComReservaExplicaSomenteEntrega() {
        autenticar("estoque:solicitacao:deliver", "estoque:solicitacao:return");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, StatusSeparacao.SEPARADO);

        Set<AcaoPermitidaSolicitacao> acoes = politica.calcular(solicitacao);

        assertThat(acoes).contains(AcaoPermitidaSolicitacao.ENTREGAR)
                .doesNotContain(AcaoPermitidaSolicitacao.CONFIRMAR_DEVOLUCAO);
    }

    @Test
    void confirmacaoDevolucaoAptaSemNecessidadeDePlanejamentoOuReserva() {
        autenticar("estoque:solicitacao:return");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, null);
        when(solicitacao.getReserva()).thenReturn(null);
        when(solicitacao.getDataPrevistaRetirada()).thenReturn(null);
        when(solicitacao.getIdEquipeSaque()).thenReturn(null);

        assertThat(politica.calcular(solicitacao))
                .contains(AcaoPermitidaSolicitacao.CONFIRMAR_DEVOLUCAO);
    }

    @Test
    void solicitacaoDevolvidaNaoOfereceNovaConfirmacao() {
        autenticar("estoque:solicitacao:return");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.DEVOLUCAO_COMUM, null);
        when(solicitacao.getReserva()).thenReturn(mock(com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica.class));
        when(solicitacao.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.DEVOLVIDO);

        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.CONFIRMAR_DEVOLUCAO);
    }

    @Test
    void comercialAprovadaNuncaExpoeSeparacao() {
        autenticar("estoque:solicitacao:separate", "estoque:solicitacao:write");
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacao.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(solicitacao.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.APROVADO);

        Set<AcaoPermitidaSolicitacao> acoes = politica.calcular(solicitacao);
        assertThat(acoes).doesNotContain(
                AcaoPermitidaSolicitacao.INICIAR_SEPARACAO,
                AcaoPermitidaSolicitacao.PULAR_SEPARACAO,
                AcaoPermitidaSolicitacao.CONCLUIR_SEPARACAO);
    }

    @Test
    void comercialPendenteSoOfereceAprovarEReprovarParaQuemAprova() {
        SolicitacaoMaterial solicitacao = comercial(SolicitacaoMaterial.StatusSolicitacao.PENDENTE);

        autenticar("estoque:solicitacao:approve");
        assertThat(politica.calcular(solicitacao))
                .contains(AcaoPermitidaSolicitacao.APROVAR, AcaoPermitidaSolicitacao.REJEITAR);

        autenticar("estoque:solicitacao:write");
        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.APROVAR, AcaoPermitidaSolicitacao.REJEITAR);
    }

    @Test
    void comercialAprovadaSoOfereceRejeitarEEntregarAoAlmoxarifado() {
        SolicitacaoMaterial solicitacao = comercial(SolicitacaoMaterial.StatusSolicitacao.APROVADO);

        autenticar("estoque:solicitacao:write");
        assertThat(politica.calcular(solicitacao))
                .contains(AcaoPermitidaSolicitacao.REJEITAR, AcaoPermitidaSolicitacao.ENTREGAR)
                .doesNotContain(AcaoPermitidaSolicitacao.APROVAR);

        autenticar("estoque:solicitacao:approve");
        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.REJEITAR, AcaoPermitidaSolicitacao.ENTREGAR, AcaoPermitidaSolicitacao.APROVAR);
    }

    private SolicitacaoMaterial comercial(SolicitacaoMaterial.StatusSolicitacao status) {
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacao.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(solicitacao.getStatus()).thenReturn(status);
        com.nexo.app.modules.estoque.domain.ItemSolicitacao item =
                mock(com.nexo.app.modules.estoque.domain.ItemSolicitacao.class);
        when(item.getQuantidadePendente()).thenReturn(java.math.BigDecimal.ONE);
        when(item.getQuantidadeEntregaLiquida()).thenReturn(java.math.BigDecimal.ZERO);
        when(solicitacao.getItens()).thenReturn(List.of(item));
        return solicitacao;
    }

    @Test
    void comercialEstornadaComSaldoPendenteExpoeNovaEntrega() {
        autenticar("estoque:solicitacao:write");
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacao.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(solicitacao.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.ESTORNADO);
        com.nexo.app.modules.estoque.domain.ItemSolicitacao item =
                mock(com.nexo.app.modules.estoque.domain.ItemSolicitacao.class);
        when(item.getQuantidadePendente()).thenReturn(java.math.BigDecimal.ONE);
        when(item.getQuantidadeEntregaLiquida()).thenReturn(java.math.BigDecimal.ZERO);
        when(solicitacao.getItens()).thenReturn(List.of(item));

        assertThat(politica.calcular(solicitacao))
                .contains(AcaoPermitidaSolicitacao.ENTREGAR)
                .doesNotContain(AcaoPermitidaSolicitacao.ESTORNAR);
    }

    @Test
    void comercialParcialComSaldoEncerradoNaoOfereceNovaEntrega() {
        autenticar("estoque:solicitacao:write");
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacao.getContexto()).thenReturn(ContextoSolicitacaoMaterial.COMERCIAL);
        when(solicitacao.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        when(solicitacao.estaEncerrada()).thenReturn(true);
        com.nexo.app.modules.estoque.domain.ItemSolicitacao item =
                mock(com.nexo.app.modules.estoque.domain.ItemSolicitacao.class);
        when(item.getQuantidadePendente()).thenReturn(java.math.BigDecimal.ONE);
        when(item.getQuantidadeEntregaLiquida()).thenReturn(java.math.BigDecimal.ONE);
        when(solicitacao.getItens()).thenReturn(List.of(item));

        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.ENTREGAR)
                .contains(AcaoPermitidaSolicitacao.ESTORNAR);
    }

    @Test
    void solicitacaoEstornadaComSaldoPendenteOfereceEntrega() {
        autenticar("estoque:solicitacao:deliver");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, StatusSeparacao.SEPARADO);
        when(solicitacao.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.ESTORNADO);

        com.nexo.app.modules.estoque.domain.ItemSolicitacao item = mock(com.nexo.app.modules.estoque.domain.ItemSolicitacao.class);
        when(item.getQuantidadePendente()).thenReturn(java.math.BigDecimal.TEN);
        when(solicitacao.getItens()).thenReturn(List.of(item));

        assertThat(politica.calcular(solicitacao))
                .contains(AcaoPermitidaSolicitacao.ENTREGAR);
    }

    @Test
    void solicitacaoComFatoOperacionalNaoOfereceEditarPlanejamento() {
        autenticar("estoque:solicitacao:plan");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, StatusSeparacao.SEPARADO);
        when(solicitacao.possuiFatoOperacional()).thenReturn(true);

        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.EDITAR_PLANEJAMENTO);
    }

    @Test
    void solicitacaoTecnicaPlanejadaSemEntregaOfereceRejeitar() {
        autenticar("estoque:solicitacao:plan");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, StatusSeparacao.PRONTO_PARA_SEPARACAO);

        assertThat(politica.calcular(solicitacao))
                .contains(AcaoPermitidaSolicitacao.REJEITAR);
    }

    @Test
    void solicitacaoEmSeparacaoSemEntregaOfereceRejeitar() {
        autenticar("estoque:solicitacao:plan");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, StatusSeparacao.EM_SEPARACAO);

        assertThat(politica.calcular(solicitacao))
                .contains(AcaoPermitidaSolicitacao.REJEITAR);
    }

    @Test
    void solicitacaoTecnicaAguardandoPlanejamentoNaoOfereceRejeitar() {
        autenticar("estoque:solicitacao:plan");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, StatusSeparacao.AGUARDANDO_AGENDAMENTO);

        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.REJEITAR);
    }

    @Test
    void solicitacaoTecnicaSemStatusSeparacaoNaoOfereceRejeitar() {
        autenticar("estoque:solicitacao:plan");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, null);

        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.REJEITAR);
    }

    @Test
    void solicitacaoTecnicaComFatoOperacionalNaoOfereceRejeitar() {
        autenticar("estoque:solicitacao:plan");
        SolicitacaoMaterial solicitacao = tecnica(TipoSolicitacaoTecnica.ADICAO_COMUM, StatusSeparacao.SEPARADO);
        when(solicitacao.possuiFatoOperacional()).thenReturn(true);

        assertThat(politica.calcular(solicitacao))
                .doesNotContain(AcaoPermitidaSolicitacao.REJEITAR);
    }

    private SolicitacaoMaterial tecnica(TipoSolicitacaoTecnica tipo, StatusSeparacao separacao) {
        SolicitacaoMaterial solicitacao = mock(SolicitacaoMaterial.class);
        when(solicitacao.getContexto()).thenReturn(ContextoSolicitacaoMaterial.TECNICO);
        when(solicitacao.getTipoTecnico()).thenReturn(tipo);
        when(solicitacao.getStatus()).thenReturn(SolicitacaoMaterial.StatusSolicitacao.APROVADO);
        when(solicitacao.getStatusSeparacao()).thenReturn(separacao);
        when(solicitacao.estaEncerrada()).thenReturn(false);
        when(solicitacao.getIdEquipeSaque()).thenReturn(UUID.randomUUID());
        when(solicitacao.getDataPrevistaRetirada()).thenReturn(LocalDate.now());
        when(solicitacao.getItens()).thenReturn(List.of());
        if (tipo.isAdicao()) {
            when(solicitacao.getReserva()).thenReturn(mock(com.nexo.app.modules.estoque.domain.ReservaSolicitacaoTecnica.class));
        }
        return solicitacao;
    }

    private void autenticar(String... autoridades) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "usuario", "n/a", java.util.Arrays.stream(autoridades).map(SimpleGrantedAuthority::new).toList()));
    }
}
