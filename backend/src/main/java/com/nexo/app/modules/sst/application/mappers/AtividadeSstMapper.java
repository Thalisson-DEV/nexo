package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.AtividadeSstResponse;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AtividadeSstMapper {

    /** Nome/função do gestor e nome do processo vêm de outros módulos: o use case preenche. */
    @Mapping(target = "nomeGestor", ignore = true)
    @Mapping(target = "funcaoGestor", ignore = true)
    @Mapping(target = "nomeProcesso", ignore = true)
    AtividadeSstResponse toResponse(AtividadeSst entity);
}
