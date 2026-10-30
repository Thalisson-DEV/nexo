package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.application.dto.MetaFaturamentoResponse;
import com.nexo.app.modules.rh.domain.MetaFaturamentoEquipe;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MetaFaturamentoMapper {

    /** Os dados da equipe e da janela vigente dependem de outras consultas e da data atual. */
    @Mapping(target = "codigoEquipe", ignore = true)
    @Mapping(target = "processo", ignore = true)
    @Mapping(target = "baseOperacional", ignore = true)
    @Mapping(target = "janelaAtualInicio", ignore = true)
    @Mapping(target = "janelaAtualFim", ignore = true)
    @Mapping(target = "diasUteisJanelaAtual", ignore = true)
    @Mapping(target = "metaDiariaAtual", ignore = true)
    MetaFaturamentoResponse toResponse(MetaFaturamentoEquipe entity);
}
