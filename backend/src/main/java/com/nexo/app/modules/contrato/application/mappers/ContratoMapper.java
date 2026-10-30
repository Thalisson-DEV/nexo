package com.nexo.app.modules.contrato.application.mappers;

import com.nexo.app.modules.contrato.application.dto.ContratoResponse;
import com.nexo.app.modules.contrato.domain.Contrato;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ContratoMapper {

    ContratoResponse toResponse(Contrato contrato);

    List<ContratoResponse> toResponseList(List<Contrato> contratos);
}
