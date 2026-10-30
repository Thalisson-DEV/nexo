package com.nexo.app.modules.execucao.application.mappers;

import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.Turno;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TurnoMapper {

    TurnoResponse toResponse(Turno entity);

    List<TurnoResponse> toResponseList(List<Turno> entities);
}
