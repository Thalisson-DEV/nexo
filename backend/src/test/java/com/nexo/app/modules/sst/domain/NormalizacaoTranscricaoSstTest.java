package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizacaoTranscricaoSstTest {
    private static final String HASH = "a".repeat(64);

    @Test
    @DisplayName("normalização conclui como rascunho e exige confirmação humana separada")
    void deveConcluirEConfirmarSemSobrescreverResultado() {
        NormalizacaoTranscricaoSst normalizacao = nova();

        normalizacao.concluir("{\"topicos\":[]}", HASH);

        assertEquals(StatusNormalizacaoTranscricaoSst.RASCUNHO, normalizacao.getStatus());
        assertNull(normalizacao.getConfirmadaEm());
        normalizacao.confirmar(UUID.randomUUID());
        assertEquals(StatusNormalizacaoTranscricaoSst.CONFIRMADA, normalizacao.getStatus());
        assertNotNull(normalizacao.getConfirmadaEm());
        assertThrows(InvarianteVioladaException.class, () -> normalizacao.confirmar(UUID.randomUUID()));
    }

    @Test
    @DisplayName("falha não aceita conteúdo posterior nem pode ser confirmada")
    void deveEncerrarFalhaSemConteudo() {
        NormalizacaoTranscricaoSst normalizacao = nova();

        normalizacao.falhar("TIMEOUT_PROVEDOR");

        assertEquals(StatusNormalizacaoTranscricaoSst.FALHA, normalizacao.getStatus());
        assertNull(normalizacao.getConteudoEstruturado());
        assertThrows(InvarianteVioladaException.class,
                () -> normalizacao.concluir("{\"topicos\":[]}", HASH));
        assertThrows(InvarianteVioladaException.class,
                () -> normalizacao.confirmar(UUID.randomUUID()));
    }

    @Test
    @DisplayName("clientRequestId só pode ser reaproveitado com o mesmo hash de insumos")
    void deveCompararInsumos() {
        NormalizacaoTranscricaoSst normalizacao = nova();

        assertEquals(true, normalizacao.mesmosInsumos(HASH));
        assertEquals(false, normalizacao.mesmosInsumos("b".repeat(64)));
    }

    private NormalizacaoTranscricaoSst nova() {
        return new NormalizacaoTranscricaoSst(UUID.randomUUID(), UUID.randomUUID(), 1,
                UUID.randomUUID(), HASH, "normalizacao-v1", UUID.randomUUID());
    }
}
