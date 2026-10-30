package com.nexo.app.modules.notificacao.application.dto;

import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.TipoAlvoSegmento;

public record SegmentoComunicadoResponse(
        UUID id,
        TipoAlvoSegmento tipoAlvo,
        String tipoAlvoRotulo,
        UUID idAlvo,
        String valorAlvo
) {
}
