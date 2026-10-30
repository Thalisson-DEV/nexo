package com.nexo.app.modules.iam.application.mappers;

import com.nexo.app.modules.iam.application.dto.UsuarioResponse;
import com.nexo.app.modules.iam.domain.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring",
        uses = {PerfilAcessoMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsuarioMapper {

    UsuarioResponse toResponse(Usuario entity);

    List<UsuarioResponse> toResponseList(List<Usuario> entities);
}
