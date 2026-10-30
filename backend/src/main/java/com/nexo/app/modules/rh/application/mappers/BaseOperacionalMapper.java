package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.domain.BaseOperacional;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {CentroFaturamentoMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BaseOperacionalMapper {

    BaseOperacionalResponse toResponse(BaseOperacional entity);

    List<BaseOperacionalResponse> toResponseList(List<BaseOperacional> entities);
}
