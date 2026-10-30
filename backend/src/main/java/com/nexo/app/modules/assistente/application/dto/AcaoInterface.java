package com.nexo.app.modules.assistente.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Acao oferecida ao usuario como botao — nunca executada pelo agente.
 *
 * <ul>
 *   <li>{@code NAVIGATE}: abre uma tela do catalogo ({@code chaveDestino} + {@code parametros}).</li>
 *   <li>{@code APPLY_FILTER}: aplica filtros numa tabela do painel aberto ({@code alvo} + {@code filtros}),
 *       validados contra as colunas que a propria tela declarou.</li>
 * </ul>
 */
public record AcaoInterface(
        String tipo,
        @JsonAlias({"chave", "destino"}) String chaveDestino,
        String rotulo,
        @JsonAlias("autoridadeObrigatoria") String autoridade,
        Map<String, Object> parametros,
        @JsonInclude(JsonInclude.Include.NON_NULL) String alvo,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FiltroProposto> filtros) {

    public static final String NAVIGATE = "NAVIGATE";
    public static final String APPLY_FILTER = "APPLY_FILTER";
    static final int MAX_FILTROS = 5;

    public AcaoInterface(String tipo, String chaveDestino, String rotulo, String autoridade, Map<String, Object> parametros) {
        this(tipo, chaveDestino, rotulo, autoridade, parametros, null, null);
    }

    public AcaoInterface {
        tipo = "NAVEGAR".equals(tipo) ? NAVIGATE : tipo;
        if (rotulo == null || rotulo.isBlank()) throw new IllegalArgumentException("Rotulo obrigatorio");
        autoridade = autoridade == null ? "" : autoridade;
        parametros = parametros == null ? Map.of() : Map.copyOf(parametros);
        if (NAVIGATE.equals(tipo)) {
            if (chaveDestino == null || !chaveDestino.matches("[A-Z][A-Z0-9_]*")) {
                throw new IllegalArgumentException("Chave de destino invalida");
            }
            alvo = null;
            filtros = List.of();
        } else if (APPLY_FILTER.equals(tipo)) {
            if (alvo == null || !alvo.matches("[A-Za-z0-9._#-]{1,80}")) throw new IllegalArgumentException("Alvo invalido");
            if (filtros == null || filtros.isEmpty() || filtros.size() > MAX_FILTROS) {
                throw new IllegalArgumentException("Filtros invalidos");
            }
            chaveDestino = null;
            filtros = List.copyOf(filtros);
        } else {
            throw new IllegalArgumentException("Tipo de acao nao suportado");
        }
    }

    public static AcaoInterface aplicarFiltro(String alvo, String rotulo, List<FiltroProposto> filtros) {
        return new AcaoInterface(APPLY_FILTER, null, rotulo, "", Map.of(), alvo, filtros);
    }

    /** Filtro proposto: {@code valor} ja convertido para o tipo da coluna (numero, texto ou booleano). */
    public record FiltroProposto(String campo, String operador, Object valor) {
        public FiltroProposto {
            if (campo == null || operador == null || valor == null) throw new IllegalArgumentException("Filtro incompleto");
        }
    }
}
