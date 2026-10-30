package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.CatalogoSstResponse;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CatalogoSstMapper {

    CatalogoSstResponse toResponse(CatalogoSst entity);
}
