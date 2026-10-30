package com.nexo.app.modules.frota.domain;

import java.util.Set;

/**
 * Categorias da CNH. As combinadas (AB, AC...) habilitam moto e o veículo da
 * segunda letra.
 */
public enum CategoriaCnh {
    A, B, C, D, E, AB, AC, AD, AE;

    private static final String ORDEM = "BCDE";

    /**
     * Indica se esta categoria permite conduzir veículos que exigem {@code exigida}.
     * Pela hierarquia do CTB, C habilita B; D habilita C e B; E habilita D, C e B.
     */
    public boolean habilita(CategoriaCnh exigida) {
        if (exigida == null) return true;
        if (exigida == this) return true;
        Set<Character> minhas = letras(this);
        if (exigida == A) return minhas.contains('A');
        char letraExigida = exigida.name().charAt(exigida.name().length() - 1);
        int nivelExigido = ORDEM.indexOf(letraExigida);
        return minhas.stream()
                .filter(c -> c != 'A')
                .anyMatch(c -> ORDEM.indexOf(c) >= nivelExigido);
    }

    private static Set<Character> letras(CategoriaCnh categoria) {
        return categoria.name().chars().mapToObj(c -> (char) c).collect(java.util.stream.Collectors.toSet());
    }
}
