package com.nexo.app.shared.infrastructure.persistence;

import com.nexo.app.shared.application.SelecaoMultipla;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utilitários compartilhados para construção de {@link org.springframework.data.jpa.domain.Specification}.
 *
 * <p>Fornece a "busca rápida" uniforme dos módulos: um único termo de texto livre
 * (parâmetro de URL {@code buscaGlobal}) é comparado via {@code LIKE} contra um ou mais
 * atributos textuais, de forma case-insensitive e accent-insensitive.</p>
 *
 * <p>A insensibilidade a acento usa a função SQL {@code immutable_unaccent(lower(coluna))}
 * (criada na migration {@code V40}); o termo é normalizado no lado Java com a mesma
 * semântica, de modo que ambos os lados da comparação fiquem equivalentes para os
 * acentos do português.</p>
 */
public final class SpecificationUtils {

    private SpecificationUtils() {
    }

    /**
     * Normaliza um texto para busca: remove acentos e converte para minúsculas.
     * Usado tanto no termo de pesquisa quanto na coluna {@code nome_busca} do Colaborador.
     */
    public static String normalizarTermo(String termo) {
        if (termo == null) {
            return null;
        }
        String semAcento = Normalizer.normalize(termo.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase();
    }

    /**
     * Traduz um filtro de múltipla seleção em um predicado {@code IN} sobre a expressão.
     * Seleção vazia ou nula significa "sem filtro" e nada é adicionado — assim o mesmo
     * código atende tanto a seleção de um único valor quanto a de vários.
     *
     * @see SelecaoMultipla
     */
    public static void filtrarPor(Collection<?> selecao, List<Predicate> predicates, Expression<?> expressao) {
        if (SelecaoMultipla.vazia(selecao)) {
            return;
        }
        predicates.add(expressao.in(selecao));
    }

    /** Envolve a expressão com {@code immutable_unaccent(lower(expr))} (casa com o índice gin_trgm). */
    private static Expression<String> normalizarColuna(CriteriaBuilder cb, Expression<String> expr) {
        return cb.function("immutable_unaccent", String.class, cb.lower(expr));
    }

    /**
     * Adiciona à lista um predicado {@code OR} de {@code LIKE} (case/accent-insensitive)
     * sobre os atributos textuais informados, lidos diretamente da {@code root}.
     * Não faz nada se o termo for nulo/vazio ou se nenhum atributo for informado.
     */
    public static <T> void buscaRapida(String termo, List<Predicate> predicates,
                                       CriteriaBuilder cb, Root<T> root, String... atributos) {
        if (termo == null || termo.isBlank() || atributos == null || atributos.length == 0) {
            return;
        }
        List<Expression<String>> campos = new ArrayList<>(atributos.length);
        for (String atributo : atributos) {
            campos.add(root.get(atributo));
        }
        buscaRapida(termo, predicates, cb, campos);
    }

    /**
     * Variante que aceita expressões arbitrárias (ex.: atributos de entidades relacionadas
     * obtidos via {@code root.join(...).get("campo")}).
     */
    public static void buscaRapida(String termo, List<Predicate> predicates,
                                   CriteriaBuilder cb, List<Expression<String>> campos) {
        Predicate predicado = predicadoBuscaRapida(termo, cb, campos);
        if (predicado != null) {
            predicates.add(predicado);
        }
    }

    /**
     * Versao composavel da busca rapida. Permite combinar os campos textuais com
     * EXISTS e blind indices dentro do mesmo OR.
     */
    public static Predicate predicadoBuscaRapida(String termo, CriteriaBuilder cb,
                                                  List<Expression<String>> campos) {
        if (termo == null || termo.isBlank() || campos == null || campos.isEmpty()) {
            return null;
        }
        String pattern = "%" + normalizarTermo(termo) + "%";
        List<Predicate> ors = new ArrayList<>(campos.size());
        for (Expression<String> campo : campos) {
            ors.add(cb.like(normalizarColuna(cb, campo), pattern));
        }
        return cb.or(ors.toArray(new Predicate[0]));
    }
}
