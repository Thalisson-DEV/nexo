package com.nexo.app.modules.contrato.application.mappers;

import com.nexo.app.modules.contrato.application.dto.AcaoValorResponse;
import com.nexo.app.modules.contrato.domain.AcaoValor;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {AcaoMapper.class, ContratoMapper.class})
public interface AcaoValorMapper {

    AcaoValorResponse toResponse(AcaoValor acaoValor);

    List<AcaoValorResponse> toResponseList(List<AcaoValor> acoesValores);
}
