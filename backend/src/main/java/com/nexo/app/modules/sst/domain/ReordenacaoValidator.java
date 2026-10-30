package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Validação utilitária para operações de reordenação: a nova ordem deve ser uma
 * permutação exata dos IDs existentes (mesmos itens, sem repetições ou ausências).
 */
final class ReordenacaoValidator {

    private ReordenacaoValidator() {}

    static void validarPermutacao(List<UUID> idsAtuais, List<UUID> novaOrdem, String elemento) {
        if (novaOrdem == null
                || novaOrdem.size() != idsAtuais.size()
                || !new HashSet<>(novaOrdem).equals(new HashSet<>(idsAtuais))) {
            throw new InvarianteVioladaException(
                    "A nova ordem de " + elemento + " deve conter exatamente os mesmos itens existentes, "
                            + "sem repetições ou ausências.");
        }
    }
}
