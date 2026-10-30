package com.nexo.app.modules.contrato.application.mappers;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.domain.Acao;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AcaoMapper {

    AcaoResponse toResponse(Acao acao);

    List<AcaoResponse> toResponseList(List<Acao> acoes);
}
