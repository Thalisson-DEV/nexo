package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;

/**
 * Projeção interna da política que o adapter de observabilidade pode avaliar sem
 * consultar o Nexo. O contrato é deliberadamente separado das respostas públicas
 * de regras e preferências, pois contém a audiência técnica.
 */
public record PoliticaObservabilidadeResponse(
        String schemaVersion,
        String version,
        OffsetDateTime generatedAt,
        OffsetDateTime validUntil,
        String trigger,
        List<Regra> rules
) {

    public record Regra(
            UUID id,
            String name,
            CategoriaNotificacao category,
            PrioridadeNotificacao priority,
            boolean enabled,
            boolean emailEnabled,
            String titleTemplate,
            String bodyTemplate,
            String deeplinkTemplate,
            List<Condicao> conditions,
            List<Destinatario> audience
    ) {
    }

    public record Condicao(String field, OperadorCondicao operator, String value) {
    }

    public record Destinatario(UUID userId, String email) {
    }
}
