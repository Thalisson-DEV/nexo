package com.nexo.app.modules.chamado.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChamadoTest {

    private final UUID solicitanteId = UUID.randomUUID();
    private final UUID atendenteId = UUID.randomUUID();

    @Test
    void deveAbrirComProtocoloEquipePrioridadeESlasDoTopico() {
        TopicoChamado topico = topico(2, 24, true);

        Chamado chamado = new Chamado(topico, "Falha no acesso",
                "O sistema informa credenciais inválidas desde hoje.", null, solicitanteId);

        assertThat(chamado.getProtocolo()).matches("CH-\\d{8}-[A-F0-9]{12}");
        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(chamado.getPrioridade()).isEqualTo(PrioridadeChamado.NORMAL);
        assertThat(chamado.getEquipeAtendimento()).isSameAs(topico.getEquipePadrao());
        assertThat(Duration.between(chamado.getDataAbertura(), chamado.getDataLimitePrimeiraResposta()))
                .isEqualTo(Duration.ofHours(2));
        assertThat(Duration.between(chamado.getDataAbertura(), chamado.getDataLimiteResolucao()))
                .isEqualTo(Duration.ofHours(24));
        assertThat(chamado.getInteracoes()).extracting(InteracaoChamado::getTipo)
                .containsExactly(TipoInteracaoChamado.ABERTURA);
    }

    @Test
    void primeiraMensagemPublicaDaCentralDeveIniciarAtendimentoEContarResposta() {
        Chamado chamado = chamadoAberto();

        chamado.registrarInteracaoAtendente(atendenteId,
                "Recebemos o chamado e iniciamos a análise.", false);

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        assertThat(chamado.getUsuarioResponsavelId()).isEqualTo(atendenteId);
        assertThat(chamado.getDataPrimeiraResposta()).isNotNull();
        assertThat(chamado.getDataInicioAtendimento()).isNotNull();
        assertThat(chamado.getInteracoes()).extracting(InteracaoChamado::getTipo)
                .contains(TipoInteracaoChamado.ATRIBUICAO,
                        TipoInteracaoChamado.ALTERACAO_STATUS,
                        TipoInteracaoChamado.MENSAGEM_PUBLICA);
    }

    @Test
    void comentarioInternoNaoDeveContarComoPrimeiraResposta() {
        Chamado chamado = chamadoAberto();

        InteracaoChamado interacao = chamado.registrarInteracaoAtendente(
                atendenteId, "Investigar log interno.", true);

        assertThat(interacao.isInterno()).isTrue();
        assertThat(chamado.getDataPrimeiraResposta()).isNull();
        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.ABERTO);
    }

    @Test
    void devePausarSlaERetomarQuandoSolicitanteResponder() {
        Chamado chamado = chamadoAberto();
        chamado.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteId, null, atendenteId);
        chamado.atualizarAtendimento(StatusChamado.AGUARDANDO_SOLICITANTE,
                null, null, null, null, atendenteId);
        var limiteAntes = chamado.getDataLimiteResolucao();

        chamado.responderPeloSolicitante(solicitanteId, "Segue a informação solicitada.");

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        assertThat(chamado.isSlaPausado()).isFalse();
        assertThat(chamado.getDataLimiteResolucao()).isAfterOrEqualTo(limiteAntes);
        assertThat(chamado.getTotalSegundosPausaSla()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void deveConcluirAtendimentoComSolucao() {
        Chamado chamado = chamadoAberto();

        chamado.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteId, null, atendenteId);
        chamado.atualizarAtendimento(StatusChamado.RESOLVIDO, null, null, null,
                "A senha foi redefinida e o acesso validado.", atendenteId);

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.RESOLVIDO);
        assertThat(chamado.getSolucao()).contains("senha foi redefinida");
        assertThat(chamado.getDataResolucao()).isNotNull();
    }

    @Test
    void naoDeveResolverSemDescreverASolucao() {
        Chamado chamado = chamadoAberto();
        chamado.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteId, null, atendenteId);

        assertThatThrownBy(() -> chamado.atualizarAtendimento(
                StatusChamado.RESOLVIDO, null, null, null, " ", atendenteId))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("solução");
        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
    }

    @Test
    void solicitanteDeveReabrirResolvidoDentroDoPrazo() {
        Chamado chamado = chamadoAberto();
        chamado.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteId, null, atendenteId);
        chamado.atualizarAtendimento(StatusChamado.RESOLVIDO, null, null, null,
                "Acesso normalizado.", atendenteId);

        chamado.reabrirPeloSolicitante(solicitanteId, "O problema voltou a ocorrer.");

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        assertThat(chamado.getSolucao()).isNull();
        assertThat(chamado.getDataResolucao()).isNull();
        assertThat(chamado.getInteracoes()).extracting(InteracaoChamado::getTipo)
                .contains(TipoInteracaoChamado.REABERTURA, TipoInteracaoChamado.MENSAGEM_PUBLICA);
    }

    @Test
    void encaminhamentoDeveTrocarEquipeELimparResponsavelAnterior() {
        Chamado chamado = chamadoAberto();
        chamado.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteId, null, atendenteId);
        EquipeAtendimento novaEquipe = equipe("INFRA", Set.of());

        chamado.atualizarAtendimento(null, novaEquipe, null, null, null, atendenteId);

        assertThat(chamado.getEquipeAtendimento()).isSameAs(novaEquipe);
        assertThat(chamado.getUsuarioResponsavelId()).isNull();
        assertThat(chamado.getInteracoes()).extracting(InteracaoChamado::getTipo)
                .contains(TipoInteracaoChamado.ENCAMINHAMENTO, TipoInteracaoChamado.ATRIBUICAO);
    }

    @Test
    void triagemDeveAlterarPrioridadeERegistrarHistorico() {
        Chamado chamado = chamadoAberto();

        chamado.atualizarAtendimento(null, null, PrioridadeChamado.CRITICA,
                null, null, atendenteId);

        assertThat(chamado.getPrioridade()).isEqualTo(PrioridadeChamado.CRITICA);
        assertThat(chamado.getInteracoes()).extracting(InteracaoChamado::getTipo)
                .contains(TipoInteracaoChamado.ALTERACAO_PRIORIDADE);
    }

    @Test
    void naoDevePularEtapasDoFluxo() {
        Chamado chamado = chamadoAberto();

        assertThatThrownBy(() -> chamado.atualizarAtendimento(
                StatusChamado.FECHADO, null, null, null, null, atendenteId))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("não é permitida");
    }

    @Test
    void solicitantePodeCancelarApenasOProprioChamadoElegivel() {
        Chamado chamado = chamadoAberto();

        assertThatThrownBy(() -> chamado.cancelarPeloSolicitante(UUID.randomUUID()))
                .isInstanceOf(InvarianteVioladaException.class);

        chamado.cancelarPeloSolicitante(solicitanteId);

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.CANCELADO);
        assertThat(chamado.getDataFechamento()).isNotNull();
    }

    @Test
    void deveLimitarQuantidadeDeEvidencias() {
        Chamado chamado = chamadoAberto();
        for (int indice = 0; indice < 10; indice++) {
            chamado.adicionarAnexo("evidencia-" + indice + ".png", "image/png", 100,
                    "chamados/teste/" + indice + ".png", solicitanteId);
        }

        assertThatThrownBy(() -> chamado.adicionarAnexo(
                "excedente.png", "image/png", 100,
                "chamados/teste/excedente.png", solicitanteId))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("10 anexos");
    }

    @Test
    void devePreservarSlaNaoVencidoAoFecharChamadoResolvidoNoPrazo() {
        Chamado chamado = chamadoAberto();
        chamado.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteId, null, atendenteId);
        chamado.atualizarAtendimento(StatusChamado.RESOLVIDO, null, null, null,
                "Problema solucionado com sucesso.", atendenteId);

        assertThat(chamado.isSlaResolucaoVencido()).isFalse();

        chamado.atualizarAtendimento(StatusChamado.FECHADO, null, null, null, null, atendenteId);

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.FECHADO);
        assertThat(chamado.getDataResolucao()).isNotNull();
        assertThat(chamado.getDataFechamento()).isNotNull();
        assertThat(chamado.isSlaResolucaoVencido()).isFalse();
    }

    @Test
    void deveConsiderarSlaPrimeiraRespostaNaoVencidoAoCancelarChamadoNoPrazo() {
        Chamado chamado = chamadoAberto();
        chamado.cancelarPeloSolicitante(solicitanteId);

        assertThat(chamado.getStatus()).isEqualTo(StatusChamado.CANCELADO);
        assertThat(chamado.getDataPrimeiraResposta()).isNull();
        assertThat(chamado.isSlaPrimeiraRespostaVencido()).isFalse();
        assertThat(chamado.isSlaResolucaoVencido()).isFalse();
    }

    private Chamado chamadoAberto() {
        return new Chamado(topico(2, 8, true), "Falha no acesso",
                "O sistema informa credenciais inválidas desde hoje.",
                PrioridadeChamado.ALTA, solicitanteId);
    }

    private TopicoChamado topico(int slaResposta, int slaResolucao, boolean pausar) {
        return new TopicoChamado("ACESSO_SENHA", "Acesso e senha",
                "Login, senha e autenticação.", PrioridadeChamado.NORMAL,
                equipe("CENTRAL", Set.of()), slaResposta, slaResolucao, pausar, 72, 10);
    }

    private EquipeAtendimento equipe(String codigo, Set<UUID> membros) {
        return new EquipeAtendimento(codigo, "Equipe " + codigo,
                "Equipe de atendimento para testes.", membros);
    }
}
