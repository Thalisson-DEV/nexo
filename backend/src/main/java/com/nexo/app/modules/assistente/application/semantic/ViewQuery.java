package com.nexo.app.modules.assistente.application.semantic;

import java.time.LocalDate;
import java.util.List;

/**
 * Consulta estruturada sobre uma view da camada semantica (sem SQL do modelo). O compilador valida
 * cada coluna contra os metadados do banco e gera SQL parametrizado.
 */
public record ViewQuery(
        String fonte,
        List<String> colunas,
        List<String> agruparPor,
        List<Metrica> metricas,
        List<Filtro> filtros,
        Periodo periodo,
        List<Ordem> ordenarPor,
        Integer limite) {

    public ViewQuery {
        colunas = colunas == null ? List.of() : List.copyOf(colunas);
        agruparPor = agruparPor == null ? List.of() : List.copyOf(agruparPor);
        metricas = metricas == null ? List.of() : List.copyOf(metricas);
        filtros = filtros == null ? List.of() : List.copyOf(filtros);
        ordenarPor = ordenarPor == null ? List.of() : List.copyOf(ordenarPor);
    }

    public boolean agregada() {
        return !metricas.isEmpty() || !agruparPor.isEmpty();
    }

    /** funcao: contagem, contagem_distinta, soma, media, minimo, maximo, mediana. */
    public record Metrica(String funcao, String coluna, String alias) {
    }

    /** operador: igual, diferente, em, nao_em, contem, maior, maior_igual, menor, menor_igual, entre, nulo, nao_nulo. */
    public record Filtro(String coluna, String operador, List<Object> valores) {
        public Filtro {
            valores = valores == null ? List.of() : List.copyOf(valores);
        }
    }

    public record Periodo(String coluna, LocalDate inicio, LocalDate fim) {
    }

    public record Ordem(String campo, boolean decrescente) {
    }
}
