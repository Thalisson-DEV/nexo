package com.nexo.app.modules.notificacao.application.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.domain.Notificacao;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificacaoMapper {

    @Mapping(target = "lida", expression = "java(entity.isLida())")
    @Mapping(target = "categoriaRotulo", expression = "java(entity.getCategoria().getRotulo())")
    @Mapping(target = "prioridadeRotulo", expression = "java(entity.getPrioridade().getRotulo())")
    NotificacaoResponse toResponse(Notificacao entity);
}
