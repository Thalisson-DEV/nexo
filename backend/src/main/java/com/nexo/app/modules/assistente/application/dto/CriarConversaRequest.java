package com.nexo.app.modules.assistente.application.dto;

import com.nexo.app.modules.assistente.application.agent.ContextoCes;
import jakarta.validation.constraints.Size;

/** Criacao explicita de conversa (opcional: a primeira mensagem tambem pode criar a conversa). */
public record CriarConversaRequest(@Size(max = 160) String titulo, ContextoCes contexto) {
}
