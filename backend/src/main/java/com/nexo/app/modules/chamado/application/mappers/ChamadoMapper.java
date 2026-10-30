package com.nexo.app.modules.chamado.application.mappers;

import com.nexo.app.modules.chamado.application.dto.AnexoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.ChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoResponse;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoResumoResponse;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoResponse;
import com.nexo.app.modules.chamado.domain.AnexoChamado;
import com.nexo.app.modules.chamado.domain.Chamado;
import com.nexo.app.modules.chamado.domain.EquipeAtendimento;
import com.nexo.app.modules.chamado.domain.InteracaoChamado;
import com.nexo.app.modules.chamado.domain.TopicoChamado;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChamadoMapper {

    @Mapping(target = "slaPrimeiraRespostaVencido", expression = "java(entity.isSlaPrimeiraRespostaVencido())")
    @Mapping(target = "slaResolucaoVencido", expression = "java(entity.isSlaResolucaoVencido())")
    @Mapping(target = "slaPausado", expression = "java(entity.isSlaPausado())")
    ChamadoResponse toResponse(Chamado entity);

    TopicoChamadoResponse toResponse(TopicoChamado entity);

    AnexoChamadoResponse toResponse(AnexoChamado entity);

    EquipeAtendimentoResponse toResponse(EquipeAtendimento entity);

    EquipeAtendimentoResumoResponse toResumo(EquipeAtendimento entity);

    InteracaoChamadoResponse toResponse(InteracaoChamado entity);
}
