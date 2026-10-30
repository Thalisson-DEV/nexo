package com.nexo.app.shared.application.lookup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.Base64;
import java.util.UUID;

/**
 * Codifica e decodifica cursores keyset sem expor os valores da ordenação na
 * URL. O primeiro byte é a versão do formato, permitindo evoluir o contrato
 * sem aceitar silenciosamente cursores de formato incompatível.
 */
public final class LookupCursorCodec {

    /** Versão do formato externo; incremente ao alterar a representação. */
    public static final int CURRENT_VERSION = 1;
    private static final int MAX_SORT_KEY_BYTES = 4_096;

    private LookupCursorCodec() {
    }

    public static String encode(String sortKey, UUID id) {
        return encode(new LookupCursor(sortKey, id));
    }

    public static String encode(LookupCursor cursor) {
        if (cursor == null) {
            throw new IllegalArgumentException("Cursor de lookup é obrigatório");
        }
        byte[] sortKey = cursor.sortKey().getBytes(StandardCharsets.UTF_8);
        if (sortKey.length > MAX_SORT_KEY_BYTES) {
            throw new IllegalArgumentException("Chave de ordenação do cursor excede o limite");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 + 2 + sortKey.length + 16);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(CURRENT_VERSION);
                output.writeShort(sortKey.length);
                output.write(sortKey);
                output.writeLong(cursor.id().getMostSignificantBits());
                output.writeLong(cursor.id().getLeastSignificantBits());
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException ex) {
            // ByteArrayOutputStream não lança IOException em memória; mantém a
            // exceção encapsulada para não vazar detalhe de implementação.
            throw new IllegalStateException("Não foi possível codificar cursor de lookup", ex);
        }
    }

    public static LookupCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw invalid();
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encoded);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
                int version = input.readUnsignedByte();
                if (version != CURRENT_VERSION) {
                    throw new InvalidLookupCursorException("Versão de cursor de lookup não suportada");
                }

                int sortKeyLength = input.readUnsignedShort();
                if (sortKeyLength == 0 || sortKeyLength > MAX_SORT_KEY_BYTES) {
                    throw invalid();
                }
                byte[] sortKey = input.readNBytes(sortKeyLength);
                if (sortKey.length != sortKeyLength) {
                    throw invalid();
                }
                UUID id = new UUID(input.readLong(), input.readLong());
                if (input.available() != 0) {
                    throw invalid();
                }
                return new LookupCursor(decodeUtf8(sortKey), id);
            }
        } catch (IllegalArgumentException ex) {
            if (ex instanceof InvalidLookupCursorException) {
                throw ex;
            }
            throw invalid(ex);
        } catch (EOFException ex) {
            throw invalid(ex);
        } catch (IOException ex) {
            throw invalid(ex);
        }
    }

    private static InvalidLookupCursorException invalid() {
        return new InvalidLookupCursorException("Cursor de lookup inválido");
    }

    private static InvalidLookupCursorException invalid(Throwable cause) {
        return new InvalidLookupCursorException("Cursor de lookup inválido", cause);
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw invalid(ex);
        }
    }
}
