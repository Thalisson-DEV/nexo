package com.nexo.app.modules.rh.application.dto;

import java.util.List;

public record ConfirmarImportacaoEquipeRequest(
    List<EquipeImportacaoDTO> equipes
) {
}
