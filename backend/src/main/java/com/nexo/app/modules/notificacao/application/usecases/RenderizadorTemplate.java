package com.nexo.app.modules.notificacao.application.usecases;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Substitui {@code {{variavel}}} pelos valores do contexto do gatilho.
 *
 * <p>Deliberadamente burro: não avalia expressões, não chama métodos, não navega
 * por propriedades. É só troca de texto. Toda a inteligência que o administrador
 * precisa está nas condições da regra, que têm operadores fechados — dar uma
 * linguagem de expressão ao template seria abrir execução de código a quem
 * configura.</p>
 *
 * <p>Placeholders inexistentes já foram recusados quando a regra foi salva
 * ({@code RegraNotificacao} valida contra as variáveis do gatilho), então aqui um
 * valor ausente significa que o gatilho não preencheu o contexto — vira texto vazio
 * em vez de expor a chave crua ao usuário final.</p>
 */
@Component
public class RenderizadorTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    public String renderizar(String template, Map<String, Object> contexto) {
        if (template == null || template.isBlank()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder resultado = new StringBuilder();
        while (matcher.find()) {
            Object valor = contexto == null ? null : contexto.get(matcher.group(1));
            String substituto = valor == null ? "" : String.valueOf(valor);
            matcher.appendReplacement(resultado, Matcher.quoteReplacement(substituto));
        }
        matcher.appendTail(resultado);

        // Substituições vazias deixam espaços duplos no meio da frase.
        return resultado.toString().replaceAll("\\s{2,}", " ").trim();
    }
}
