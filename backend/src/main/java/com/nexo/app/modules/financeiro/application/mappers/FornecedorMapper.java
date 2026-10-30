package com.nexo.app.modules.financeiro.application.mappers;

import com.nexo.app.modules.financeiro.application.dto.FornecedorResponse;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FornecedorMapper {

    /**
     * {@code utilizavel} combina status e soft delete para que o frontend não
     * precise repetir a regra ao desabilitar a opção no combo.
     */
    @Mapping(target = "utilizavel", expression = "java(entity.isAtivo() && entity.getStatus().isUtilizavel())")
    FornecedorResponse toResponse(Fornecedor entity);
}
