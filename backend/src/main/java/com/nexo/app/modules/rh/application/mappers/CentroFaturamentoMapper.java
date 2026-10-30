package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.CentroFaturamentoResponse;
import com.nexo.app.modules.rh.domain.CentroFaturamento;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CentroFaturamentoMapper {
    CentroFaturamentoResponse toResponse(CentroFaturamento entity);
    List<CentroFaturamentoResponse> toResponseList(List<CentroFaturamento> entities);
}
