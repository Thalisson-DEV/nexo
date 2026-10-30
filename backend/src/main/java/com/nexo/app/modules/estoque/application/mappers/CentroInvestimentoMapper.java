package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.CentroInvestimentoResponse;
import com.nexo.app.modules.estoque.domain.CentroInvestimento;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CentroInvestimentoMapper {
    CentroInvestimentoResponse toResponse(CentroInvestimento entity);
    List<CentroInvestimentoResponse> toResponseList(List<CentroInvestimento> entities);
}
