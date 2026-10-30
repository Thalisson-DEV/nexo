package com.nexo.app.modules.desconto.application.mappers;

import com.nexo.app.modules.desconto.application.dto.MotivoDescontoResponse;
import com.nexo.app.modules.desconto.domain.MotivoDesconto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MotivoDescontoMapper {

    MotivoDescontoResponse toResponse(MotivoDesconto entity);
}
