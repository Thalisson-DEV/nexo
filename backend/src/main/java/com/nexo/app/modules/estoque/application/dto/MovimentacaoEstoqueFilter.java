package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial;
import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque.TipoMovimentacao;
import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial.StatusSolicitacao;
import com.nexo.app.modules.estoque.domain.StatusSeparacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MovimentacaoEstoqueFilter(
    UUID idEstoque, // Filtra se for origem OU destino

    @Parameter(description = "Base operacional do estoque de origem")
    UUID idBaseOrigem,

    @Parameter(description = "Base operacional do estoque de destino")
    UUID idBaseDestino,

    UUID idMaterial,
    CondicaoMaterial condicao,
    TipoMovimentacao tipo,
    OffsetDateTime dataInicio,
    OffsetDateTime dataFim,
    String referencia,
    UUID idUsuarioOperador,
    String numSerie,
    ContextoSolicitacaoMaterial contexto,
    UUID idSolicitacao,
    TipoSolicitacaoTecnica tipoTecnico,
    TipoObraSnapshot tipoObra,
    UUID idObra,
    String ocorrencia,
    UUID idEquipe,
    UUID idReserva,
    String numeroReserva,
    UUID idAgenteReserva,
    StatusSolicitacao statusSolicitacao,
    StatusSeparacao statusSeparacao,

    @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive)")
    String buscaGlobal
) {
    public MovimentacaoEstoqueFilter(
            UUID idEstoque,
            UUID idBaseOrigem,
            UUID idBaseDestino,
            UUID idMaterial,
            CondicaoMaterial condicao,
            TipoMovimentacao tipo,
            OffsetDateTime dataInicio,
            OffsetDateTime dataFim,
            String referencia,
            UUID idUsuarioOperador,
            String numSerie,
            String buscaGlobal
    ) {
        this(idEstoque, idBaseOrigem, idBaseDestino, idMaterial, condicao, tipo, dataInicio, dataFim,
                referencia, idUsuarioOperador, numSerie, null, null, null, null, null, null, null, null,
                null, null, null, null, buscaGlobal);
    }
}
