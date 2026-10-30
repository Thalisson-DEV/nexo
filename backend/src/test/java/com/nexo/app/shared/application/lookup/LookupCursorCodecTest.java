package com.nexo.app.shared.application.lookup;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupCursorCodecTest {

    @Test
    void codificaCursorOpacoERecuperaChaveNormalizadaEId() {
        UUID id = UUID.randomUUID();

        String encoded = LookupCursorCodec.encode("  Árvore ", id);
        LookupCursor decoded = LookupCursorCodec.decode(encoded);

        assertThat(decoded.sortKey()).isEqualTo("arvore");
        assertThat(decoded.id()).isEqualTo(id);
        assertThat(encoded).doesNotContain("Árvore", "arvore", id.toString());
    }

    @Test
    void rejeitaCursorNuloVazioCorrompidoETruncado() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> LookupCursorCodec.encode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LookupCursorCodec.decode(null))
                .isInstanceOf(InvalidLookupCursorException.class);
        assertThatThrownBy(() -> LookupCursorCodec.decode("not-a-cursor"))
                .isInstanceOf(InvalidLookupCursorException.class);

        String encoded = LookupCursorCodec.encode("nome", id);
        assertThatThrownBy(() -> LookupCursorCodec.decode(encoded.substring(0, encoded.length() - 2)))
                .isInstanceOf(InvalidLookupCursorException.class);
    }

    @Test
    void rejeitaVersaoDesconhecidaEBytesExtras() {
        UUID id = UUID.randomUUID();
        String encoded = LookupCursorCodec.encode("nome", id);
        byte[] payload = Base64.getUrlDecoder().decode(encoded);

        payload[0] = 2;
        assertThatThrownBy(() -> LookupCursorCodec.decode(
                Base64.getUrlEncoder().withoutPadding().encodeToString(payload)))
                .isInstanceOf(InvalidLookupCursorException.class)
                .hasMessageContaining("não suportada");

        byte[] original = Base64.getUrlDecoder().decode(encoded);
        byte[] extras = java.util.Arrays.copyOf(original, original.length + 1);
        extras[extras.length - 1] = 1;
        assertThatThrownBy(() -> LookupCursorCodec.decode(
                Base64.getUrlEncoder().withoutPadding().encodeToString(extras)))
                .isInstanceOf(InvalidLookupCursorException.class);
    }

    @Test
    void rejeitaChaveDeOrdenacaoAusente() {
        assertThatThrownBy(() -> new LookupCursor(" ", UUID.randomUUID()))
                .isInstanceOf(InvalidLookupCursorException.class);
    }
}
