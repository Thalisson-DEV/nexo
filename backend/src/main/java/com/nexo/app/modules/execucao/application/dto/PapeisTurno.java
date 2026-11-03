package com.nexo.app.modules.execucao.application.dto;

import java.util.Set;
import java.util.UUID;

/** Líder e substitutos de um turno; substituto é quem está no turno e não é titular da equipe. */
public record PapeisTurno(UUID idTurno, UUID idLider, Set<UUID> idsSubstitutos) {
}
