package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.domain.ProcessoNegocio;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProcessoNegocioMapper {
    ProcessoNegocioResponse toResponse(ProcessoNegocio entity);
    List<ProcessoNegocioResponse> toResponseList(List<ProcessoNegocio> entities);
}