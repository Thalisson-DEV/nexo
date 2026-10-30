package com.nexo.app.modules.assistente.application.dto;

import com.nexo.app.modules.assistente.application.agent.ContextoCes;
import com.nexo.app.modules.assistente.application.artifacts.ArtifactService;
import com.nexo.app.modules.assistente.domain.OrigemConversa;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Conversa completa para o workspace: mensagens com timeline, artefatos e arquivos. Quando ha execucao em
 * andamento, {@code execucaoEmAndamento} indica a mensagem que pode ser retomada em {@code GET /stream}.
 */
public record ConversaDetalheResponse(
        UUID id,
        String titulo,
        OffsetDateTime dataCriacao,
        OffsetDateTime ultimaAtividadeEm,
        ContextoCes contexto,
        List<MensagemResponse> mensagens,
        List<ArtifactService.ArtefatoView> artefatos,
        List<ArquivoAssistenteResponse> arquivos,
        UUID execucaoEmAndamento,
        OrigemConversa origem,
        String origemId) {
}
