package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComunicadoTest {

    @Test
    void deveNascerRascunhoSemJanela() {
        Comunicado comunicado = novoComunicado();

        assertThat(comunicado.getStatus()).isEqualTo(StatusComunicado.RASCUNHO);
        assertThat(comunicado.getDataInicio()).isNull();
        assertThat(comunicado.estaVigente(OffsetDateTime.now())).isFalse();
    }

    @Test
    void naoDevePublicarSemPublicoAlvo() {
        Comunicado comunicado = novoComunicado();

        assertThatThrownBy(() -> comunicado.publicar(null, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("público-alvo");
    }

    @Test
    void publicarSemDataDeveEntrarNoArImediatamente() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.TODOS, null, null);

        comunicado.publicar(null, null);

        assertThat(comunicado.getStatus()).isEqualTo(StatusComunicado.PUBLICADO);
        assertThat(comunicado.getPublicadoEm()).isNotNull();
        assertThat(comunicado.estaVigente(OffsetDateTime.now())).isTrue();
        assertThat(comunicado.estaAgendado(OffsetDateTime.now())).isFalse();
    }

    @Test
    void publicarComInicioFuturoDeveFicarAgendadoSemEstadoProprio() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.TODOS, null, null);

        comunicado.publicar(OffsetDateTime.now().plusDays(2), null);

        assertThat(comunicado.getStatus()).isEqualTo(StatusComunicado.PUBLICADO);
        assertThat(comunicado.estaAgendado(OffsetDateTime.now())).isTrue();
        assertThat(comunicado.estaVigente(OffsetDateTime.now())).isFalse();
        assertThat(comunicado.estaVigente(OffsetDateTime.now().plusDays(3))).isTrue();
    }

    @Test
    void deveRecusarJanelaComFimAntesDoInicio() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.TODOS, null, null);
        OffsetDateTime inicio = OffsetDateTime.now().plusDays(2);

        assertThatThrownBy(() -> comunicado.publicar(inicio, inicio.minusDays(1)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("posterior ao início");
    }

    @Test
    void comunicadoForaDaJanelaDeixaDeSerVigente() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.TODOS, null, null);
        comunicado.publicar(OffsetDateTime.now().minusDays(2), OffsetDateTime.now().plusDays(1));

        assertThat(comunicado.estaVigente(OffsetDateTime.now())).isTrue();
        assertThat(comunicado.estaVigente(OffsetDateTime.now().plusDays(2))).isFalse();
    }

    @Test
    void todosNaoDeveConviverComOutrosSegmentos() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.PERFIL, UUID.randomUUID(), null);

        assertThatThrownBy(() -> comunicado.adicionarSegmento(TipoAlvoSegmento.TODOS, null, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("já abrange");
    }

    @Test
    void segmentoNaoDeveSerDuplicado() {
        Comunicado comunicado = novoComunicado();
        UUID idPerfil = UUID.randomUUID();
        comunicado.adicionarSegmento(TipoAlvoSegmento.PERFIL, idPerfil, null);

        assertThatThrownBy(() -> comunicado.adicionarSegmento(TipoAlvoSegmento.PERFIL, idPerfil, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("já foi adicionado");
    }

    @Test
    void segmentoDeveExigirOAlvoQueOTipoPede() {
        Comunicado comunicado = novoComunicado();

        assertThatThrownBy(() -> comunicado.adicionarSegmento(TipoAlvoSegmento.PERFIL, null, null))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> comunicado.adicionarSegmento(TipoAlvoSegmento.TIPO_USUARIO, null, "  "))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void publicadoNaoDeveAceitarEdicaoCompletaNemMudancaDePublico() {
        Comunicado comunicado = comunicadoPublicado();

        assertThatThrownBy(() -> comunicado.atualizar("Outro", null, "Corpo",
                TipoComunicado.AVISO, ApresentacaoComunicado.CARD, null, null, null,
                false, false, false, false, PrioridadeNotificacao.NORMAL))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("rascunho");

        assertThatThrownBy(() -> comunicado.adicionarSegmento(TipoAlvoSegmento.PERFIL, UUID.randomUUID(), null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("rascunho");
    }

    @Test
    void publicadoDeveAceitarCorrecaoDeTexto() {
        Comunicado comunicado = comunicadoPublicado();

        comunicado.corrigirTexto("Título corrigido", "Resumo novo", "Conteúdo corrigido");

        assertThat(comunicado.getTitulo()).isEqualTo("Título corrigido");
        assertThat(comunicado.getStatus()).isEqualTo(StatusComunicado.PUBLICADO);
    }

    @Test
    void arquivadoNaoDeveAceitarNemCorrecaoNemAnexo() {
        Comunicado comunicado = comunicadoPublicado();
        comunicado.arquivar();

        assertThatThrownBy(() -> comunicado.corrigirTexto("X", null, "Y"))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> comunicado.adicionarAnexo("a.png", "image/png", 10, "chave"))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void comunicadoQueExigeConfirmacaoNaoPodeSerAdiado() {
        Comunicado normal = novoComunicado();
        Comunicado obrigatorio = new Comunicado("Aviso de segurança", null, "Leia com atenção.",
                TipoComunicado.URGENTE, ApresentacaoComunicado.MODAL, null, null, null,
                false, true, false, false, PrioridadeNotificacao.CRITICA);

        assertThat(normal.permiteAdiar()).isTrue();
        assertThat(obrigatorio.permiteAdiar()).isFalse();
    }

    @Test
    void deveRecusarLinkDeVideoComEsquemaPerigoso() {
        assertThatThrownBy(() -> new Comunicado("Novidades", null, "Veja o vídeo.",
                TipoComunicado.CHANGELOG, ApresentacaoComunicado.CARD,
                "javascript:alert(1)", null, null, false, false, false, false, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("http");
    }

    @Test
    void acaoDeveTerRotuloEDestinoOuNenhumDosDois() {
        assertThatThrownBy(() -> new Comunicado("Novidades", null, "Corpo",
                TipoComunicado.CHANGELOG, ApresentacaoComunicado.CARD, null,
                "Ver detalhes", null, false, false, false, false, null))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("rótulo e destino");
    }

    private Comunicado novoComunicado() {
        return new Comunicado("Novidades da versão 2.4", "Confira o que mudou",
                "Agora o mural existe.", TipoComunicado.CHANGELOG, ApresentacaoComunicado.CARD,
                "https://youtu.be/exemplo", "Ver detalhes", "/changelog", false, false, false, false,
                PrioridadeNotificacao.NORMAL);
    }

    private Comunicado comunicadoPublicado() {
        Comunicado comunicado = novoComunicado();
        comunicado.adicionarSegmento(TipoAlvoSegmento.TODOS, null, null);
        comunicado.publicar(null, null);
        return comunicado;
    }
}
