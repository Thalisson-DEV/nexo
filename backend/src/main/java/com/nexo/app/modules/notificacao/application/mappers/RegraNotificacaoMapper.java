package com.nexo.app.modules.notificacao.application.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoResponse;
import com.nexo.app.modules.notificacao.domain.CondicaoRegra;
import com.nexo.app.modules.notificacao.domain.DestinatarioRegra;
import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RegraNotificacaoMapper {

    /**
     * O nome do gatilho vem do catálogo, não do banco. Uma regra apontando para
     * gatilho removido continua listável — com a chave crua no lugar do nome — para
     * que o administrador possa vê-la e corrigi-la.
     */
    @Mapping(target = "gatilhoNome", expression = "java(nomeDoGatilho(entity))")
    @Mapping(target = "modo", expression = "java(entity.getModo())")
    @Mapping(target = "permiteEmail", expression = "java(entity.getCategoria().permiteEmail())")
    @Mapping(target = "condicoes", expression = "java(toCondicoes(entity.condicoesAtivas()))")
    @Mapping(target = "destinatarios", expression = "java(toDestinatarios(entity.destinatariosAtivos()))")
    RegraNotificacaoResponse toResponse(RegraNotificacao entity);

    @Mapping(target = "operadorRotulo", expression = "java(entity.getOperador().getRotulo())")
    RegraNotificacaoResponse.CondicaoResponse toResponse(CondicaoRegra entity);

    @Mapping(target = "tipoRotulo", expression = "java(entity.getTipo().getRotulo())")
    RegraNotificacaoResponse.DestinatarioResponse toResponse(DestinatarioRegra entity);

    List<RegraNotificacaoResponse.CondicaoResponse> toCondicoes(List<CondicaoRegra> condicoes);

    List<RegraNotificacaoResponse.DestinatarioResponse> toDestinatarios(List<DestinatarioRegra> destinatarios);

    default String nomeDoGatilho(RegraNotificacao entity) {
        return GatilhoNotificacao.porChave(entity.getChaveGatilho())
                .map(GatilhoNotificacao::getNome)
                .orElse(entity.getChaveGatilho());
    }
}
