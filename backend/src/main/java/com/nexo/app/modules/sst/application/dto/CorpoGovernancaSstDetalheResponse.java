package com.nexo.app.modules.sst.application.dto;

import java.util.List;

public record CorpoGovernancaSstDetalheResponse(
        CorpoGovernancaSstResponse corpo,
        List<BaseAbrangidaCorpoSstResponse> basesAbrangidas,
        List<MembroCorpoSstResponse> membros
) {
}
