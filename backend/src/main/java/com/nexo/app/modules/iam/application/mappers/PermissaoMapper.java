package com.nexo.app.modules.iam.application.mappers;

import com.nexo.app.modules.iam.application.dto.PermissaoResponse;
import com.nexo.app.modules.iam.domain.Permissao;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissaoMapper {
    PermissaoResponse toResponse(Permissao permissao);
}
