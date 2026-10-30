package com.nexo.app.modules.estoque.application.mappers;

import com.nexo.app.modules.estoque.application.dto.MovimentacaoEstoqueResponse;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoItemResponse;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoOperacaoSummaryResponse;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {EstoqueMapper.class, MaterialMapper.class})
public interface MovimentacaoEstoqueMapper {
    @Mapping(target = "contexto", source = "solicitacao.contexto")
    @Mapping(target = "idSolicitacao", source = "solicitacao.id")
    @Mapping(target = "tipoObra", source = "tipoObraSnapshot")
    @Mapping(target = "idReserva", source = "reserva.id")
    @Mapping(target = "idOperacao", source = "operacaoSolicitacao.id")
    @Mapping(target = "idItemOperacao", source = "itemOperacao.id")
    MovimentacaoEstoqueResponse toResponse(MovimentacaoEstoque entity);

    @Mapping(target = "material", source = "material")
    @Mapping(target = "idItemOperacao", source = "itemOperacao.id")
    @Mapping(target = "idItemSolicitacao", source = "itemOperacao.itemSolicitacao.id")
    MovimentacaoItemResponse toItemResponse(MovimentacaoEstoque entity);

    @Mapping(target = "idReferenciaOperacao", source = "idReferenciaOperacao")
    @Mapping(target = "tipo", source = "tipo")
    @Mapping(target = "dataHora", source = "dataHora")
    @Mapping(target = "estoqueOrigem", source = "estoqueOrigem")
    @Mapping(target = "estoqueDestino", source = "estoqueDestino")
    @Mapping(target = "operadorNome", ignore = true)
    @Mapping(target = "contexto", source = "solicitacao.contexto")
    @Mapping(target = "idSolicitacao", source = "solicitacao.id")
    @Mapping(target = "tipoObra", source = "tipoObraSnapshot")
    @Mapping(target = "idReserva", source = "reserva.id")
    @Mapping(target = "idOperacao", source = "operacaoSolicitacao.id")
    MovimentacaoOperacaoSummaryResponse toOperacaoSummaryResponse(MovimentacaoEstoque entity);
}
