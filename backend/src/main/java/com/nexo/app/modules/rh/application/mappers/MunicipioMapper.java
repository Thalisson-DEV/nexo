package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.MunicipioResponse;
import com.nexo.app.modules.rh.domain.Municipio;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {BaseOperacionalMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MunicipioMapper {

    MunicipioResponse toResponse(Municipio municipio);

    List<MunicipioResponse> toResponseList(List<Municipio> municipios);
}
