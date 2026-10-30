package com.nexo.app.modules.obras.application.mappers;

import com.nexo.app.modules.obras.application.dto.TipoAnexoObraResponse;
import com.nexo.app.modules.obras.domain.TipoAnexoObra;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TipoAnexoObraMapper {

    TipoAnexoObraResponse toResponse(TipoAnexoObra entity);

    List<TipoAnexoObraResponse> toResponseList(List<TipoAnexoObra> entities);
}
