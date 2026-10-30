package com.nexo.app.modules.sst.application.dto;

import java.util.List;
import java.util.UUID;

public record TratamentoDesviosEmMassaResponse(
        int totalAtualizados,
        List<UUID> idsAtualizados
) {
}
