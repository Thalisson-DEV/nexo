package com.nexo.app.modules.execucao.application.mappers;

import com.nexo.app.modules.execucao.application.dto.ExecucaoAcaoResponse;
import com.nexo.app.modules.execucao.application.dto.ExecucaoComercialResponse;
import com.nexo.app.modules.execucao.application.dto.ExecucaoMaterialResponse;
import com.nexo.app.modules.execucao.domain.ExecucaoAcao;
import com.nexo.app.modules.execucao.domain.ExecucaoComercial;
import com.nexo.app.modules.execucao.domain.ExecucaoMaterial;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ExecucaoComercialMapper {

    @Mapping(target = "equipe", ignore = true)
    @Mapping(target = "municipio", ignore = true)
    @Mapping(target = "tipoServico", ignore = true)
    ExecucaoComercialResponse toResponse(ExecucaoComercial entity);

    List<ExecucaoComercialResponse> toResponseList(List<ExecucaoComercial> entities);

    @Mapping(target = "acao", ignore = true)
    ExecucaoAcaoResponse toAcaoResponse(ExecucaoAcao entity);

    @Mapping(target = "material", ignore = true)
    ExecucaoMaterialResponse toMaterialResponse(ExecucaoMaterial entity);
}
