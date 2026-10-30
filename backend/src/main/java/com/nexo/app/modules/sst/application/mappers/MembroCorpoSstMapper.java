package com.nexo.app.modules.sst.application.mappers;

import com.nexo.app.modules.sst.application.dto.MembroCorpoSstResponse;
import com.nexo.app.modules.sst.domain.MembroCorpoSst;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MembroCorpoSstMapper {
    MembroCorpoSstResponse toResponse(MembroCorpoSst entity);
}
