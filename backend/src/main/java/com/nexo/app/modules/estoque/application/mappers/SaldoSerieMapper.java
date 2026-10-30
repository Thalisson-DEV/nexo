package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.SaldoSerieResponse;
import com.nexo.app.modules.estoque.domain.SaldoSerie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", 
        uses = {EstoqueMapper.class, MaterialMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SaldoSerieMapper {
    // A série só muda de registro ao mudar de estoque ou de condição: a última alteração marca a chegada.
    @Mapping(target = "noSaldoDesde", expression = "java(entity.getDataUltimaAlteracao() != null ? entity.getDataUltimaAlteracao() : entity.getDataCriacao())")
    @Mapping(target = "reservadaParaSolicitacao", expression = "java(entity.getReservaVigente().map(r -> r.getSolicitacao().getId()).orElse(null))")
    SaldoSerieResponse toResponse(SaldoSerie entity);
    List<SaldoSerieResponse> toResponseList(List<SaldoSerie> entities);
}
