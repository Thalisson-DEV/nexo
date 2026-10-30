package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.BaseAbrangidaCorpoSstResponse;
import com.nexo.app.modules.sst.domain.BaseAbrangidaCorpoSst;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BaseAbrangidaCorpoSstMapper {
    BaseAbrangidaCorpoSstResponse toResponse(BaseAbrangidaCorpoSst entity);
}
