package com.nexo.app.modules.rh.application.mappers;

import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.rh.domain.Veiculo;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", 
        uses = {BaseOperacionalMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VeiculoMapper {
    VeiculoResponse toResponse(Veiculo entity);
    List<VeiculoResponse> toResponseList(List<Veiculo> entities);
}
