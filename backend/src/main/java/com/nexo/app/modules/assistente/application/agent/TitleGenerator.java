package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.modules.assistente.application.llm.LlmMessage;
import com.nexo.app.modules.assistente.application.llm.LlmProvider;
import com.nexo.app.modules.assistente.application.llm.LlmRequest;
import com.nexo.app.modules.assistente.application.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Gera o titulo da conversa a partir da primeira pergunta (como o Onyx faz ao fim do primeiro turno).
 * Falhas nunca afetam a resposta: a conversa mantem o titulo provisorio.
 */
@Component
public class TitleGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TitleGenerator.class);
    private static final String INSTRUCAO = """
            Crie um titulo curto e especifico, em portugues do Brasil, para uma conversa de trabalho no sistema Nexo que \
            comeca com a mensagem do usuario abaixo. Regras: de 2 a 6 palavras, sem aspas, sem ponto final, sem emojis, \
            sem a palavra "conversa". Responda somente com o titulo.""";

    private final LlmProvider llm;
    private final AssistenteProperties properties;

    public TitleGenerator(LlmProvider llm, AssistenteProperties properties) {
        this.llm = llm;
        this.properties = properties;
    }

    public Optional<String> gerar(String pergunta) {
        if (!llm.isConfigured() || pergunta == null || pergunta.isBlank()) return Optional.empty();
        String entrada = pergunta.length() > 2_000 ? pergunta.substring(0, 2_000) : pergunta;
        AssistenteProperties.Llm config = properties.getLlm();
        String esforco = config.getTitleReasoningEffort();
        try {
            LlmResponse resposta = llm.complete(new LlmRequest(config.effectiveTitleModel(),
                    List.of(new LlmMessage.System(INSTRUCAO), new LlmMessage.User(entrada)), List.of(),
                    LlmRequest.ToolChoice.AUTO, 0.2, esforco == null || esforco.isBlank() ? 400 : 60, null,
                    esforco == null || esforco.isBlank() ? null : esforco));
            return limpar(resposta.content());
        } catch (RuntimeException exception) {
            LOGGER.info("Titulo automatico indisponivel: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> limpar(String bruto) {
        if (bruto == null) return Optional.empty();
        String titulo = bruto.strip().lines().filter(linha -> !linha.isBlank()).findFirst().orElse("");
        titulo = titulo.replaceFirst("(?i)^t[ií]tulo\\s*:\\s*", "")
                .replaceAll("[\"“”'`*#]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("[.。]+$", "")
                .strip();
        if (titulo.isEmpty()) return Optional.empty();
        if (titulo.length() > 80) titulo = titulo.substring(0, 77).strip() + "...";
        return Optional.of(Character.toUpperCase(titulo.charAt(0)) + titulo.substring(1));
    }
}
