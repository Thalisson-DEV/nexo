package com.nexo.app.modules.assistente.application.dto;

import java.util.List;

/**
 * O que o Copiloto consegue fazer para o usuario atual (ferramentas autorizadas e operacionais,
 * competencias e sugestoes para a tela inicial). Nao expoe permissoes nem detalhes internos.
 */
public record CapacidadesResponse(
        boolean disponivel,
        String motivoIndisponibilidade,
        String modelo,
        boolean python,
        boolean pesquisaWeb,
        boolean leituraWeb,
        List<Ferramenta> ferramentas,
        List<Competencia> competencias,
        List<Sugestao> sugestoes,
        Limites limites) {

    public record Ferramenta(String nome, String titulo, String tipo) {
    }

    public record Competencia(String nome, String titulo, String descricao) {
    }

    public record Sugestao(String titulo, String descricao, String pergunta, String icone) {
    }

    public record Limites(long arquivoMaxBytes, int anexosPorMensagem, List<String> formatosAceitos) {
    }
}
