package com.nexo.app.shared.domain.events;

import java.util.Map;
import java.util.UUID;

/**
 * Evento de domínio para transporte de dados de auditoria entre módulos.
 * Essencial para manter o baixo acoplamento exigido pelo Spring Modulith.
 */
public record AuditEvent(
    String tabela,
    UUID registroId,
    String operacao,
    Map<String, Object> dadosAnteriores,
    Map<String, Object> dadosNovos
) {}
