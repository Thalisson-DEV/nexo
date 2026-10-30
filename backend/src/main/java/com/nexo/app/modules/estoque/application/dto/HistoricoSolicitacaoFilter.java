package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import com.nexo.app.modules.estoque.domain.TipoEventoSolicitacao;
import com.nexo.app.modules.estoque.domain.TipoObraSnapshot;
import com.nexo.app.modules.estoque.domain.TipoSolicitacaoTecnica;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Filtros para a linha do tempo de solicitações de material")
public record HistoricoSolicitacaoFilter(
        OffsetDateTime inicio,
        OffsetDateTime fim,
        TipoSolicitacaoTecnica tipoTecnico,
        TipoObraSnapshot tipoObra,
        String ocorrencia,
        TipoEventoSolicitacao tipoEvento,
        UUID idAutor,
        OrigemComandoTecnico origem,
        UUID idItem
) {
}
