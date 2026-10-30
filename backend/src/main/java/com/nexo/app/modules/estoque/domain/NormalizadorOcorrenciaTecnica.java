package com.nexo.app.modules.estoque.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Forma canonica da chave textual de ocorrencia, sem consultar cadastro externo. */
public final class NormalizadorOcorrenciaTecnica {

    public static final int TAMANHO_MAXIMO = 100;

    private NormalizadorOcorrenciaTecnica() {
    }

    public static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        rejeitarControle(valor);

        String nfkc = Normalizer.normalize(valor, Normalizer.Form.NFKC);
        rejeitarControle(nfkc);

        StringBuilder canonico = new StringBuilder(nfkc.length());
        boolean espacoPendente = false;
        for (int indice = 0; indice < nfkc.length();) {
            int codigo = nfkc.codePointAt(indice);
            indice += Character.charCount(codigo);
            if (Character.isWhitespace(codigo) || Character.isSpaceChar(codigo)) {
                espacoPendente = canonico.length() > 0;
                continue;
            }
            if (espacoPendente) {
                canonico.append(' ');
                espacoPendente = false;
            }
            canonico.appendCodePoint(codigo);
        }

        String resultado = canonico.toString().toUpperCase(Locale.ROOT);
        if (resultado.isEmpty()) {
            return null;
        }
        if (resultado.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "A ocorrencia deve ter no maximo " + TAMANHO_MAXIMO + " caracteres.");
        }
        return resultado;
    }

    private static void rejeitarControle(String valor) {
        valor.codePoints().forEach(codigo -> {
            if (Character.getType(codigo) == Character.CONTROL) {
                throw new IllegalArgumentException("A ocorrencia nao pode conter caracteres de controle.");
            }
        });
    }
}
