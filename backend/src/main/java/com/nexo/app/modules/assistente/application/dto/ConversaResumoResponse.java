package com.nexo.app.modules.assistente.application.dto;

import com.nexo.app.modules.assistente.domain.OrigemConversa;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Item da lista de conversas do workspace. {@code origem}/{@code origemId} indicam se a conversa comecou
 * em um painel (dashboard) — ela continua sendo uma conversa normal, visivel no historico geral.
 */
public record ConversaResumoResponse(
        UUID id,
        String titulo,
        OffsetDateTime ultimaAtividadeEm,
        OffsetDateTime dataCriacao,
        boolean emAndamento,
        OrigemConversa origem,
        String origemId) {
}
