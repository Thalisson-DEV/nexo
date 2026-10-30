package com.nexo.app.modules.sst.application.dto;

import java.util.UUID;

public record BaseAbrangidaCorpoSstResponse(
        UUID id,
        UUID idBaseOperacional,
        String baseNome
) {
}
