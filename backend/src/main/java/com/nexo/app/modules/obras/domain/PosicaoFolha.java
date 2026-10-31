package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.math.BigDecimal;

/**
 * Validação de posição na folha: página a partir de 1 e coordenadas em [0,1] da página sem rotação.
 */
final class PosicaoFolha {

    private PosicaoFolha() {
    }

    static void validar(Integer pagina, BigDecimal x, BigDecimal y) {
        if (pagina == null || pagina < 1) {
            throw new InvarianteVioladaException("A página deve ser a partir de 1.");
        }
        if (foraDaFolha(x) || foraDaFolha(y)) {
            throw new InvarianteVioladaException("A posição deve estar dentro da folha.");
        }
    }

    private static boolean foraDaFolha(BigDecimal valor) {
        return valor == null || valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) > 0;
    }
}
