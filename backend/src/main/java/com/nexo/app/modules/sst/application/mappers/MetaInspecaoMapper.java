package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.MetaInspecaoResponse;
import com.nexo.app.modules.sst.domain.MetaInspecao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MetaInspecaoMapper {

    /**
     * Os nomes do alvo e a janela vigente dependem de outros módulos/da data atual e
     * são preenchidos pelo use case.
     */
    @Mapping(target = "nomeTipoInspecao", ignore = true)
    @Mapping(target = "nomeFuncao", ignore = true)
    @Mapping(target = "nomeColaborador", ignore = true)
    @Mapping(target = "janelaAtualInicio", ignore = true)
    @Mapping(target = "janelaAtualFim", ignore = true)
    MetaInspecaoResponse toResponse(MetaInspecao entity);
}
