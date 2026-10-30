package com.nexo.app.modules.automacao.application.motor;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ContextoGatilho(UUID automacaoId, String automacaoNome, UUID donoId, OffsetDateTime agora) {
}
