package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.EstoqueResponse;
import com.nexo.app.modules.estoque.domain.Estoque;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", 
        uses = {TipoEstoqueMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EstoqueMapper {
    EstoqueResponse toResponse(Estoque entity);
    List<EstoqueResponse> toResponseList(List<Estoque> entities);
}
