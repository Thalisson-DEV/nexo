package com.nexo.app.modules.iam.application.mappers;

import com.nexo.app.modules.iam.application.dto.PerfilAcessoResponse;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PerfilAcessoMapper {

    PerfilAcessoResponse toResponse(PerfilAcesso entity);

    List<PerfilAcessoResponse> toResponseList(List<PerfilAcesso> entities);
}
