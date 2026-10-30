package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detalhamento completo de uma operação de movimentação (Lote)")
public record MovimentacaoOperacaoResponse(
    @Schema(description = "ID de referência da operação")
    UUID idReferenciaOperacao,
    
    TipoMovimentacao tipo,
    
    OffsetDateTime dataHora,
    
    @Schema(description = "Nome do usuário que realizou a operação")
    String operadorNome,
    
    EstoqueResponse estoqueOrigem,
    
    EstoqueResponse estoqueDestino,

    @Schema(description = "Contexto da solicitacao que originou a operacao; nulo em ajustes avulsos")
    ContextoSolicitacaoMaterial contexto,


    UUID idSolicitacao,

    TipoSolicitacaoTecnica tipoTecnico,

    @Schema(description = "Snapshot do tipo da obra no instante da operacao")
    TipoObraSnapshot tipoObra,

    String ocorrencia,

    UUID idReserva,

    UUID idOperacao,

    UUID idObra,

    UUID idEquipe,

    @Schema(description = "Lista de materiais movimentados nesta operação")
    List<MovimentacaoItemResponse> itens,

    @Schema(description = "Tipo ou status da assinatura do estoque entregador: FISICA, DIGITAL ou nulo")
    String assinaturaEntregador,

    @Schema(description = "Tipo ou status da assinatura do estoque recebedor: FISICA, DIGITAL ou nulo")
    String assinaturaRecebedor
) {}
