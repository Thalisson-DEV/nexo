package com.nexo.app.modules.lookup.application.dto;

import com.nexo.app.shared.application.dto.LookupOptionDto;

import java.util.List;
import java.util.Objects;

/** Catalogos pequenos e relativamente estaticos carregados em uma unica requisicao. */
public record LookupBootstrapDto(
        List<LookupOptionDto> operationalBases,
        List<LookupOptionDto> movementTypes,
        List<LookupOptionDto> requestTypes,
        List<LookupOptionDto> statuses,
        List<LookupOptionDto> processes
) {
    public LookupBootstrapDto {
        operationalBases = immutable(operationalBases, "operationalBases");
        movementTypes = immutable(movementTypes, "movementTypes");
        requestTypes = immutable(requestTypes, "requestTypes");
        statuses = immutable(statuses, "statuses");
        processes = immutable(processes, "processes");
    }

    private static List<LookupOptionDto> immutable(List<LookupOptionDto> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " e obrigatorio"));
    }
}
