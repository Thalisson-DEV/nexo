package com.nexo.app.shared.domain.events;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Incidente sustentado aberto pelo plano de observabilidade.
 *
 * <p>O evento carrega apenas contexto sanitizado e nomes lógicos. O módulo de
 * notificações resolve a audiência pela regra configurada, portanto o monitor
 * não precisa conhecer usuários, permissões ou endereços de e-mail.</p>
 */
public record ObservabilityIncidenteAbertoEvent(
        UUID idIncidente,
        String ambiente,
        String componente,
        String estadoInicial,
        String resumo,
        String linkStatus,
        String runbook
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "observability.incidente_aberto";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idIncidente;
    }

    @Override
    public Map<String, Object> contexto() {
        return Map.of(
                "ambiente", texto(ambiente),
                "componente", texto(componente),
                "estadoInicial", texto(estadoInicial),
                "resumo", texto(resumo),
                "linkStatus", texto(linkStatus),
                "runbook", texto(runbook));
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        return Map.of();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
