package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.rh.domain.Funcao;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FuncaoMapper {

    FuncaoResponse toResponse(Funcao entity);

    List<FuncaoResponse> toResponseList(List<Funcao> entities);
}
