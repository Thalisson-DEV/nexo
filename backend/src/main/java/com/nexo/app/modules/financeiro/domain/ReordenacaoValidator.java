package com.nexo.app.modules.financeiro.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Garante que uma lista de IDs enviada para reordenação é uma permutação exata
 * dos elementos existentes — nem faltando, nem sobrando, nem repetido.
 *
 * <p>Reordenar é operação de apresentação: preserva IDs e referências, portanto é
 * permitida mesmo em formulários já utilizados.</p>
 */
final class ReordenacaoValidator {

    private ReordenacaoValidator() {
    }

    static void validarPermutacao(List<UUID> existentes, List<UUID> informados, String elemento) {
        if (informados == null || informados.isEmpty()) {
            throw new InvarianteVioladaException("Informe a nova ordem dos " + elemento + ".");
        }
        Set<UUID> unicos = new HashSet<>(informados);
        if (unicos.size() != informados.size()) {
            throw new InvarianteVioladaException("A ordem informada possui " + elemento + " repetidos.");
        }
        if (unicos.size() != existentes.size() || !unicos.containsAll(existentes)) {
            throw new InvarianteVioladaException(
                    "A ordem informada precisa conter exatamente os " + elemento + " existentes.");
        }
    }
}
