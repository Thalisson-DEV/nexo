package com.nexo.app.modules.execucao.domain;

import java.util.List;
import java.util.UUID;

/**
 * O que entrou e o que saiu da nota numa alteração. Material com a quantidade mudada
 * aparece só pela diferença: 2 a mais entra em adicionados, 2 a menos em removidos.
 */
public record AlteracaoLancamentos(
        List<UUID> acoesAdicionadas,
        List<UUID> acoesRemovidas,
        List<MaterialLancado> materiaisAdicionados,
        List<MaterialLancado> materiaisRemovidos
) {
    public AlteracaoLancamentos {
        acoesAdicionadas = List.copyOf(acoesAdicionadas);
        acoesRemovidas = List.copyOf(acoesRemovidas);
        materiaisAdicionados = List.copyOf(materiaisAdicionados);
        materiaisRemovidos = List.copyOf(materiaisRemovidos);
    }

    public boolean isVazia() {
        return acoesAdicionadas.isEmpty() && acoesRemovidas.isEmpty()
                && materiaisAdicionados.isEmpty() && materiaisRemovidos.isEmpty();
    }
}
