package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstResponse;
import com.nexo.app.modules.sst.domain.CorpoGovernancaSst;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CorpoGovernancaSstMapper {

    CorpoGovernancaSstResponse toResponse(CorpoGovernancaSst entity);
}
