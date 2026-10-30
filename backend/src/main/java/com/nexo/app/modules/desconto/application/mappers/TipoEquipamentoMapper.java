package com.nexo.app.modules.desconto.application.mappers;

import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoResponse;
import com.nexo.app.modules.desconto.domain.TipoEquipamento;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TipoEquipamentoMapper {

    TipoEquipamentoResponse toResponse(TipoEquipamento entity);
}
