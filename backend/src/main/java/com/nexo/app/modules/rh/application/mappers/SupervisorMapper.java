package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.SupervisorResponse;
import com.nexo.app.modules.rh.application.dto.SupervisorMinResponse;
import com.nexo.app.modules.rh.domain.Supervisor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {ColaboradorMapper.class, CoordenadorMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SupervisorMapper {

    SupervisorResponse toResponse(Supervisor entity);

    List<SupervisorResponse> toResponseList(List<Supervisor> entities);

    @Mapping(target = "idColaborador", source = "colaborador.id")
    @Mapping(target = "nomeColaborador", source = "colaborador.nomeCompleto")
    @Mapping(target = "idCoordenador", source = "coordenador.id")
    @Mapping(target = "nomeCoordenador", source = "coordenador.colaborador.nomeCompleto")
    SupervisorMinResponse toMinResponse(Supervisor entity);

    List<SupervisorMinResponse> toMinResponseList(List<Supervisor> entities);
}
