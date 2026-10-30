package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteracaoComunicadoTest {

    private final UUID usuario = UUID.randomUUID();

    @Test
    void dispensarDeveEncerrarOAssuntoEDescartarAdiamentoPendente() {
        InteracaoComunicado interacao = interacaoDe(comunicado(false));
        interacao.adiarAte(OffsetDateTime.now().plusDays(1));

        interacao.dispensar();

        assertThat(interacao.isDispensado()).isTrue();
        assertThat(interacao.getAdiadoAte())
                .as("um adiamento vivo faria o comunicado ressurgir depois de dispensado")
                .isNull();
    }

    @Test
    void dispensarDuasVezesDevePreservarOPrimeiroInstante() {
        InteracaoComunicado interacao = interacaoDe(comunicado(false));

        interacao.dispensar();
        OffsetDateTime primeiro = interacao.getDispensadoEm();
        interacao.dispensar();

        assertThat(interacao.getDispensadoEm()).isEqualTo(primeiro);
    }

    @Test
    void qualquerAcaoDeveRegistrarVisualizacao() {
        InteracaoComunicado interacao = interacaoDe(comunicado(false));

        interacao.adiarAte(OffsetDateTime.now().plusHours(2));

        assertThat(interacao.getVisualizadoEm()).isNotNull();
    }

    @Test
    void adiarDeveExigirDataFutura() {
        InteracaoComunicado interacao = interacaoDe(comunicado(false));

        assertThatThrownBy(() -> interacao.adiarAte(OffsetDateTime.now().minusMinutes(1)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("futura");
    }

    @Test
    void naoDeveAdiarComunicadoQueExigeConfirmacao() {
        InteracaoComunicado interacao = interacaoDe(comunicado(true));

        assertThatThrownBy(() -> interacao.adiarAte(OffsetDateTime.now().plusDays(1)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("confirmação");
    }

    @Test
    void confirmarDeveRegistrarEDispensar() {
        InteracaoComunicado interacao = interacaoDe(comunicado(true));

        interacao.confirmar();

        assertThat(interacao.isConfirmado()).isTrue();
        assertThat(interacao.isDispensado())
                .as("exigir um segundo clique para fechar o que a pessoa acabou de confirmar é atrito")
                .isTrue();
    }

    @Test
    void naoDeveConfirmarComunicadoQueNaoExigeConfirmacao() {
        InteracaoComunicado interacao = interacaoDe(comunicado(false));

        assertThatThrownBy(interacao::confirmar)
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("não exige confirmação");
    }

    private InteracaoComunicado interacaoDe(Comunicado comunicado) {
        return new InteracaoComunicado(comunicado, usuario);
    }

    private Comunicado comunicado(boolean exigeConfirmacao) {
        return new Comunicado("Aviso", null, "Conteúdo do aviso.",
                TipoComunicado.AVISO, ApresentacaoComunicado.MODAL, null, null, null,
                false, exigeConfirmacao, false, false, PrioridadeNotificacao.NORMAL);
    }
}
