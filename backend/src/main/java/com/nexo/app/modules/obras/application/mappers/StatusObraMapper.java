package com.nexo.app.modules.obras.application.mappers;

import com.nexo.app.modules.obras.application.dto.StatusObraResponse;
import com.nexo.app.modules.obras.domain.StatusObra;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface StatusObraMapper {

    StatusObraResponse toResponse(StatusObra entity);

    List<StatusObraResponse> toResponseList(List<StatusObra> entities);
}
