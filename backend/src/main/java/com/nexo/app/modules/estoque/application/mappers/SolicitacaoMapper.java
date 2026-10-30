package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.SolicitacaoResponse;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoSummaryResponse;
import com.nexo.app.modules.estoque.domain.EtapaOperacionalSolicitacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.ItemSolicitacao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = EtapaOperacionalSolicitacao.class,
        uses = {EstoqueMapper.class, MaterialMapper.class, MotivoRejeicaoMapper.class})
public interface SolicitacaoMapper {

    @Mapping(target = "assinaturaAlmox", ignore = true)
    @Mapping(target = "assinaturaEquipe", ignore = true)
    @Mapping(target = "versao", expression = "java(entity.getVersao() == null ? 0L : entity.getVersao())")
    @Mapping(target = "acoesPermitidas", ignore = true)
    @Mapping(target = "operacoes", ignore = true)
    @Mapping(target = "dataPrevista", source = "dataPrevistaRetirada")
    @Mapping(target = "foraPrazoSaque", source = "foraPrazoSaque")
    @Mapping(target = "idEquipe", source = "idEquipeSaque")
    @Mapping(target = "motivoPendencia", expression = "java(entity.getMotivoPendencia() == null ? null : com.nexo.app.modules.estoque.application.dto.MotivoPendenciaResponse.de(entity.getMotivoPendencia()))")
    @Mapping(target = "possuiPendencia", expression = "java(entity.possuiPendencia())")
    SolicitacaoResponse toResponse(SolicitacaoMaterial entity);

    @Mapping(target = "estoqueOrigemNome", source = "estoqueOrigem.nomeIdentificador")
    @Mapping(target = "estoqueDestinoNome", source = "estoqueDestino.nomeIdentificador")
    @Mapping(target = "totalItens", expression = "java(entity.getItens() != null ? entity.getItens().size() : 0)")
    @Mapping(target = "versao", expression = "java(entity.getVersao() == null ? 0L : entity.getVersao())")
    @Mapping(target = "etapaOperacional", expression = "java(EtapaOperacionalSolicitacao.calcular(entity))")
    @Mapping(target = "dataPrevista", source = "dataPrevistaRetirada")
    @Mapping(target = "foraPrazoSaque", source = "foraPrazoSaque")
    @Mapping(target = "idEquipe", source = "idEquipeSaque")
    @Mapping(target = "acoesPermitidas", ignore = true)
    @Mapping(target = "possuiPendencia", expression = "java(entity.possuiPendencia())")
    @Mapping(target = "motivosPendencia", expression = "java(entity.nomesMotivosPendencia())")
    SolicitacaoSummaryResponse toSummaryResponse(SolicitacaoMaterial entity);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "motivoPendencia", expression = "java(entity.getMotivoPendencia() == null ? null : com.nexo.app.modules.estoque.application.dto.MotivoPendenciaResponse.de(entity.getMotivoPendencia()))")
    @Mapping(target = "seriesReservadas", expression = "java(entity.getSeriesReservadas())")
    SolicitacaoResponse.ItemSolicitacaoResponse toItemResponse(ItemSolicitacao entity);
}
