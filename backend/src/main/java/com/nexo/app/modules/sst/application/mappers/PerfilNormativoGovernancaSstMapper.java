package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstResponse;
import com.nexo.app.modules.sst.domain.PerfilNormativoGovernancaSst;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PerfilNormativoGovernancaSstMapper {
    PerfilNormativoGovernancaSstResponse toResponse(PerfilNormativoGovernancaSst entity);
}
