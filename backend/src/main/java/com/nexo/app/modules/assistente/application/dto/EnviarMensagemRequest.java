package com.nexo.app.modules.assistente.application.dto;

import com.nexo.app.modules.assistente.application.agent.ContextoCes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Pergunta enviada ao Copiloto.
 *
 * @param clientMessageId identificador gerado pelo cliente (idempotencia: reenvios nao duplicam respostas)
 * @param conteudo        texto da pergunta
 * @param arquivos        anexos previamente enviados em {@code POST /v1/assistente/arquivos}
 * @param contexto        pagina/entidade de onde o usuario perguntou (opcional)
 */
public record EnviarMensagemRequest(
        @NotNull UUID clientMessageId,
        @NotBlank @Size(max = 20_000) String conteudo,
        @Size(max = 10) List<UUID> arquivos,
        ContextoCes contexto) {

    public EnviarMensagemRequest {
        arquivos = arquivos == null ? List.of() : List.copyOf(arquivos);
    }
}
