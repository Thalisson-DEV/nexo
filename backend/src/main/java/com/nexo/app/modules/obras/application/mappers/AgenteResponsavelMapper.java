package com.nexo.app.modules.obras.application.mappers;

import com.nexo.app.modules.obras.application.dto.AgenteResponsavelResponse;
import com.nexo.app.modules.obras.domain.AgenteResponsavel;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AgenteResponsavelMapper {

    AgenteResponsavelResponse toResponse(AgenteResponsavel entity);

    List<AgenteResponsavelResponse> toResponseList(List<AgenteResponsavel> entities);
}
