package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.SaldoResponse;
import com.nexo.app.modules.estoque.domain.Saldo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", 
        uses = {EstoqueMapper.class, MaterialMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SaldoMapper {
    @Mapping(target = "ultimaMovimentacao", expression = "java(entity.getDataUltimaAlteracao() != null ? entity.getDataUltimaAlteracao() : entity.getDataCriacao())")
    SaldoResponse toResponse(Saldo entity);
    List<SaldoResponse> toResponseList(List<Saldo> entities);
}
