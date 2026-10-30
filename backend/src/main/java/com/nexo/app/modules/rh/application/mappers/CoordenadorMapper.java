package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.CoordenadorResponse;
import com.nexo.app.modules.rh.application.dto.CoordenadorMinResponse;
import com.nexo.app.modules.rh.domain.Coordenador;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {ColaboradorMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CoordenadorMapper {

    CoordenadorResponse toResponse(Coordenador entity);

    List<CoordenadorResponse> toResponseList(List<Coordenador> entities);

    @Mapping(target = "idColaborador", source = "colaborador.id")
    @Mapping(target = "nomeColaborador", source = "colaborador.nomeCompleto")
    CoordenadorMinResponse toMinResponse(Coordenador entity);

    List<CoordenadorMinResponse> toMinResponseList(List<Coordenador> entities);
}
