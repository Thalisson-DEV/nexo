package com.nexo.app.modules.desconto.application.mappers;

import com.nexo.app.modules.desconto.application.dto.DescontoAnexoResponse;
import com.nexo.app.modules.desconto.application.dto.DescontoItemResponse;
import com.nexo.app.modules.desconto.application.dto.DescontoResponse;
import com.nexo.app.modules.desconto.domain.Desconto;
import com.nexo.app.modules.desconto.domain.DescontoAnexo;
import com.nexo.app.modules.desconto.domain.DescontoItem;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring",
        uses = {MotivoDescontoMapper.class, TipoEquipamentoMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DescontoMapper {

    DescontoResponse toResponse(Desconto entity);

    DescontoItemResponse toResponse(DescontoItem entity);

    DescontoAnexoResponse toResponse(DescontoAnexo entity);
}
