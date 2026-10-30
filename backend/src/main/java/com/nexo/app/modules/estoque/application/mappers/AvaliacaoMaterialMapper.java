package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.AvaliacaoMaterialResponse;
import com.nexo.app.modules.estoque.domain.AvaliacaoMaterial;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AvaliacaoMaterialMapper {
    AvaliacaoMaterialResponse toResponse(AvaliacaoMaterial entity);
    List<AvaliacaoMaterialResponse> toResponseList(List<AvaliacaoMaterial> entities);
}
