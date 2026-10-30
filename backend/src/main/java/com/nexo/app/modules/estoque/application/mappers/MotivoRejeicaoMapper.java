package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoResponse;
import com.nexo.app.modules.estoque.domain.MotivoRejeicao;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MotivoRejeicaoMapper {
    MotivoRejeicaoResponse toResponse(MotivoRejeicao entity);
}
