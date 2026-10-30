package com.nexo.app.modules.estoque.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TipoEventoSolicitacaoCoberturaTest {

    @ParameterizedTest(name = "evento {0} pode compor a cadeia canonica")
    @EnumSource(TipoEventoSolicitacao.class)
    void todosOsTiposDaEnumeracaoPodemComporEventoImutavel(TipoEventoSolicitacao tipo) {
        UUID idSolicitacao = UUID.randomUUID();

        EventoSolicitacaoMaterial evento = new EventoSolicitacaoMaterial(
                idSolicitacao,
                1,
                tipo,
                TipoSolicitacaoTecnica.ADICAO_COMUM,
                TipoObraSnapshot.CORRETIVA,
                UUID.randomUUID(),
                "OC 1042",
                UUID.randomUUID(),
                "USUARIO TESTE",
                OrigemComandoTecnico.UI,
                null,
                0,
                "{}",
                OffsetDateTime.parse("2026-08-24T10:00:00-03:00"));

        assertThat(evento.getTipoEvento()).isEqualTo(tipo);
        assertThat(evento.getIdSolicitacao()).isEqualTo(idSolicitacao);
        assertThat(evento.getTipoTecnico()).isEqualTo("221");
    }
}
