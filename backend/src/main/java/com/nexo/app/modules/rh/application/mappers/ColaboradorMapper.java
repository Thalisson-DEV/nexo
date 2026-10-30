package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.domain.Colaborador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        uses = {ProcessoNegocioMapper.class, BaseOperacionalMapper.class, FuncaoMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ColaboradorMapper {

    @Mapping(target = "nomeCompleto", expression = "java(entity.getNomeCompleto())")
    ColaboradorResponse toResponse(Colaborador entity);
}
