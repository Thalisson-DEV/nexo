package com.nexo.app.shared.application;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Apoio aos filtros de múltipla seleção das listagens e dashboards.
 *
 * <p>Um filtro de múltipla seleção chega repetindo o mesmo parâmetro na URL
 * ({@code ?idProcesso=a&idProcesso=b}) ou separando os valores por vírgula
 * ({@code ?idProcesso=a,b}). Seleção nula ou vazia significa <em>sem filtro</em>:
 * nada é restringido — o mesmo comportamento que o filtro singular tinha quando
 * vinha nulo, o que mantém as chamadas antigas funcionando.</p>
 *
 * <p>O frontend Angular envia posições não inicializadas como {@code undefined},
 * {@code null} ou vazio, que o {@code StringToUuidConverter} traduz para {@code null}.
 * Por isso as seleções são normalizadas no construtor canônico de cada filtro: a
 * partir dali nenhum consumidor precisa se defender de listas nulas ou com buracos.</p>
 */
public final class SelecaoMultipla {

    private SelecaoMultipla() {
    }

    /**
     * Forma canônica de uma seleção: lista imutável, nunca nula, sem nulos e sem
     * repetições. Deve ser aplicada no construtor compacto dos records de filtro.
     */
    public static <T> List<T> normalizar(Collection<T> selecao) {
        if (selecao == null || selecao.isEmpty()) {
            return List.of();
        }
        return selecao.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * {@code true} quando não há filtro (seleção vazia) ou o valor está entre os escolhidos.
     * Um valor nulo — o registro não tem a dimensão preenchida — nunca casa com uma
     * seleção explícita, e por isso fica de fora do resultado filtrado.
     */
    public static <T> boolean contem(Collection<T> selecao, T valor) {
        if (vazia(selecao)) {
            return true;
        }
        return valor != null && selecao.contains(valor);
    }

    public static boolean vazia(Collection<?> selecao) {
        return selecao == null || selecao.isEmpty();
    }
}
