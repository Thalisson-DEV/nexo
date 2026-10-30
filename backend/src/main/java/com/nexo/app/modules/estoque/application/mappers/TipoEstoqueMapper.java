package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.TipoEstoqueResponse;
import com.nexo.app.modules.estoque.domain.TipoEstoque;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TipoEstoqueMapper {
    TipoEstoqueResponse toResponse(TipoEstoque entity);
    List<TipoEstoqueResponse> toResponseList(List<TipoEstoque> entities);
}
