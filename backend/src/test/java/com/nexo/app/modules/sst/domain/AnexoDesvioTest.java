package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnexoDesvioTest {

    private DesvioSeguranca novoDesvio() {
        InspecaoSeguranca insp = new InspecaoSeguranca(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                UUID.randomUUID(), OffsetDateTime.now(), null);
        UUID colab = UUID.randomUUID();
        insp.adicionarColaboradorInspecionado(colab);
        return insp.registrarDesvio(UUID.randomUUID(), UUID.randomUUID(), GravidadeDesvio.ALTA, "d", "a", null, List.of(colab));
    }

    @Test
    @DisplayName("Imagem deve ser classificada como FOTO e documento como DOCUMENTO")
    void classificaTipoPeloContentType() {
        DesvioSeguranca desvio = novoDesvio();

        AnexoDesvio foto = desvio.adicionarAnexo(ContextoAnexo.DESVIO, "foto.jpg", "image/jpeg", 1024, "k1");
        AnexoDesvio doc = desvio.adicionarAnexo(ContextoAnexo.ACAO_CORRETIVA, "laudo.pdf", "application/pdf", 2048, "k2");

        assertEquals(TipoAnexo.FOTO, foto.getTipo());
        assertEquals(TipoAnexo.DOCUMENTO, doc.getTipo());
        assertEquals(2, desvio.getAnexos().size());
        assertEquals(ContextoAnexo.ACAO_CORRETIVA, doc.getContexto());
    }

    @Test
    @DisplayName("Deve rejeitar anexo sem nome de arquivo")
    void rejeitaNomeEmBranco() {
        DesvioSeguranca desvio = novoDesvio();
        assertThrows(InvarianteVioladaException.class,
                () -> desvio.adicionarAnexo(ContextoAnexo.DESVIO, "  ", "image/jpeg", 10, "k"));
    }

    @Test
    @DisplayName("Deve rejeitar anexo com tamanho zero ou negativo")
    void rejeitaTamanhoInvalido() {
        DesvioSeguranca desvio = novoDesvio();
        assertThrows(InvarianteVioladaException.class,
                () -> desvio.adicionarAnexo(ContextoAnexo.DESVIO, "foto.jpg", "image/jpeg", 0, "k"));
    }

    @Test
    @DisplayName("Deve remover um anexo do desvio")
    void removeAnexo() {
        DesvioSeguranca desvio = novoDesvio();
        AnexoDesvio anexo = desvio.adicionarAnexo(ContextoAnexo.DESVIO, "foto.jpg", "image/jpeg", 10, "k");
        assertEquals(1, desvio.getAnexos().size());

        desvio.removerAnexo(anexo);

        assertEquals(0, desvio.getAnexos().size());
    }
}
