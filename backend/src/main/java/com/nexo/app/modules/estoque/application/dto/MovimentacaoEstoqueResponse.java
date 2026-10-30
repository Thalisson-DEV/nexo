package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MovimentacaoEstoqueResponse(
    UUID id,
    EstoqueResponse estoqueOrigem,
    EstoqueResponse estoqueDestino,
    MaterialResponse material,
    CondicaoMaterial condicao,
    BigDecimal quantidade,
    TipoMovimentacao tipo,
    UUID idUsuarioOperador,
    UUID idReferenciaOperacao,
    OffsetDateTime dataHora,
    @Schema(description = "Contexto da solicitacao que originou o movimento; nulo em ajustes avulsos")
    ContextoSolicitacaoMaterial contexto,
    UUID idSolicitacao,
    TipoSolicitacaoTecnica tipoTecnico,
    @Schema(description = "Snapshot do tipo da obra no instante da operacao")
    TipoObraSnapshot tipoObra,
    String ocorrencia,
    UUID idReserva,
    UUID idOperacao,
    UUID idItemOperacao,
    UUID idObra,
    UUID idEquipe
) {}
