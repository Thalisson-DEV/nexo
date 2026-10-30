package com.nexo.app.shared.domain.events;

import java.util.UUID;

/** Snapshot minimo para provisionar a custodia de materiais da equipe. */
public record EquipeCriadaEvent(UUID idEquipe, String codigo, UUID idBaseOperacional) {
}
