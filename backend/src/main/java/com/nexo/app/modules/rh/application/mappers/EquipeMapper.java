package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.EquipeMembroResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.domain.Equipe;
import com.nexo.app.modules.rh.domain.EquipeMembro;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {
    TipoEquipeMapper.class,
    VeiculoMapper.class,
    CoordenadorMapper.class,
    SupervisorMapper.class,
    ColaboradorMapper.class,
    BaseOperacionalMapper.class,
    ProcessoNegocioMapper.class
})
public interface EquipeMapper {

    /** A meta vem do cadastro de metas de faturamento (MetaFaturamentoUseCases.comMetaVigente). */
    @Mapping(target = "metaFaturamentoMensal", ignore = true)
    @Mapping(target = "metaFaturamentoDiaria", ignore = true)
    EquipeResponse toResponse(Equipe equipe);

    List<EquipeResponse> toResponseList(List<Equipe> equipes);

    @Mapping(target = "idColaborador", source = "colaborador.id")
    @Mapping(target = "nomeColaborador", source = "colaborador.nomeCompleto")
    @Mapping(target = "vinculoAtivo", expression = "java(entity.isVinculoAtivo())")
    EquipeMembroResponse toMembroResponse(EquipeMembro entity);

    List<EquipeMembroResponse> toMembroResponseList(List<EquipeMembro> membros);
}
