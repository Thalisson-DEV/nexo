package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.application.dto.TipoEquipeRequest;
import com.nexo.app.modules.rh.TipoEquipeResponse;
import com.nexo.app.modules.rh.domain.TipoEquipe;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TipoEquipeMapper {

    TipoEquipeResponse toResponse(TipoEquipe tipoEquipe);

    List<TipoEquipeResponse> toResponseList(List<TipoEquipe> tiposEquipe);
}
