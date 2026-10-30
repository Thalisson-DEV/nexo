package com.nexo.app.modules.frota.application.mappers;

import com.nexo.app.modules.frota.application.dto.TipoImplementoResponse;
import com.nexo.app.modules.frota.application.dto.TipoVeiculoResponse;
import com.nexo.app.modules.frota.domain.TipoImplemento;
import com.nexo.app.modules.frota.domain.TipoVeiculo;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CatalogoFrotaMapper {

    TipoVeiculoResponse toResponse(TipoVeiculo entity);

    TipoImplementoResponse toResponse(TipoImplemento entity);
}
