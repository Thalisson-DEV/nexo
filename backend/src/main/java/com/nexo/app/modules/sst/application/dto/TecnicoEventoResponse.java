package com.nexo.app.modules.sst.application.dto;

import java.util.UUID;

/**
 * Técnico de segurança responsável pelo evento, como estava no registro.
 */
public record TecnicoEventoResponse(
        UUID id,
        UUID idColaborador,
        String nome,
        String funcao
) {
}
