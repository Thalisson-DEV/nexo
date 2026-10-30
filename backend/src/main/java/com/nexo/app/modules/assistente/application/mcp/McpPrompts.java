package com.nexo.app.modules.assistente.application.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/** Prompts prontos do Nexo que o aplicativo de IA oferece como atalho (ex.: "/" no Claude). */
final class McpPrompts {

    private record Argumento(String nome, String descricao, boolean obrigatorio) {
    }

    private record Prompt(String nome, String titulo, String descricao, List<Argumento> argumentos, boolean escrita,
            String modelo) {
    }

    private static final List<Prompt> PROMPTS = List.of(
            new Prompt("panorama_do_dia", "Panorama do dia",
                    "O que pede a atenção da pessoa hoje no Nexo, nas áreas que ela acompanha.", List.of(), false,
                    "Faça um panorama de hoje no Nexo para mim: o que está pendente, atrasado ou fora do normal nas "
                            + "áreas que eu acompanho. Traga os números das ferramentas do Nexo e termine com as três "
                            + "coisas que eu deveria olhar primeiro."),
            new Prompt("analisar_area", "Analisar uma área",
                    "Análise de uma área do Nexo num período, com comparação e pontos de atenção.",
                    List.of(new Argumento("area", "Área: estoque, solicitações, execução, faturamento, descontos, SST, "
                                    + "frota, chamados, financeiro, obras...", true),
                            new Argumento("periodo", "Período, ex.: este mês, últimos 30 dias, 2026", false)),
                    false,
                    "Analise {area} no Nexo {periodo}. Compare com o período anterior, aponte o que mudou, o que está "
                            + "fora do normal e o que eu deveria fazer. Use só números das ferramentas do Nexo."),
            new Prompt("cadastrar_no_ces", "Cadastrar no Nexo",
                    "Cadastra ou altera algo no Nexo, conferindo os dados com a pessoa antes de gravar.",
                    List.of(new Argumento("pedido", "O que cadastrar ou alterar, com os detalhes que você já tem", true)),
                    true,
                    "Quero cadastrar no Nexo: {pedido}. Descubra no Nexo o que mais é necessário, me mostre um resumo "
                            + "do que será gravado e só grave depois que eu confirmar."));

    private McpPrompts() {
    }

    static ObjectNode listar(ObjectMapper mapper, boolean permiteEscrita) {
        ObjectNode resultado = mapper.createObjectNode();
        ArrayNode lista = resultado.putArray("prompts");
        for (Prompt prompt : PROMPTS) {
            if (prompt.escrita() && !permiteEscrita) continue;
            ObjectNode item = lista.addObject();
            item.put("name", prompt.nome());
            item.put("title", prompt.titulo());
            item.put("description", prompt.descricao());
            ArrayNode argumentos = item.putArray("arguments");
            for (Argumento argumento : prompt.argumentos()) {
                argumentos.addObject().put("name", argumento.nome()).put("description", argumento.descricao())
                        .put("required", argumento.obrigatorio());
            }
        }
        return resultado;
    }

    static ObjectNode obter(ObjectMapper mapper, JsonNode params) {
        String nome = params.path("name").asText("");
        Prompt prompt = PROMPTS.stream().filter(p -> p.nome().equals(nome)).findFirst()
                .orElseThrow(() -> new McpServidor.ErroMcp(McpServidor.INVALID_PARAMS, "Prompt desconhecido: " + nome));
        String texto = prompt.modelo();
        JsonNode valores = params.path("arguments");
        for (Argumento argumento : prompt.argumentos()) {
            String valor = valores.path(argumento.nome()).asText("").strip();
            if (valor.isEmpty() && argumento.obrigatorio()) {
                throw new McpServidor.ErroMcp(McpServidor.INVALID_PARAMS, "Informe " + argumento.nome() + ".");
            }
            if (valor.isEmpty() && argumento.nome().equals("periodo")) valor = "no último mês";
            texto = texto.replace("{" + argumento.nome() + "}", valor);
        }
        ObjectNode resultado = mapper.createObjectNode();
        resultado.put("description", prompt.descricao());
        resultado.putArray("messages").addObject().put("role", "user")
                .putObject("content").put("type", "text").put("text", texto);
        return resultado;
    }
}
