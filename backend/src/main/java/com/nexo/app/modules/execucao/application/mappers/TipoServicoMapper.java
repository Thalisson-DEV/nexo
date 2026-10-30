package com.nexo.app.modules.execucao.application.mappers;

import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import com.nexo.app.modules.execucao.domain.TipoServico;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TipoServicoMapper {

    TipoServicoResponse toResponse(TipoServico entity);
}
