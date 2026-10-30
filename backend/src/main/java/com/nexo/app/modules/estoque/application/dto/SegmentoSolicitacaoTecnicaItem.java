package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Segmento agregado da fila tecnica; o almoxarifado representa somente a rota de atendimento")
public record SegmentoSolicitacaoTecnicaItem(
        String contexto,
        String tipoTecnico,
        String tipoObra,
        String ocorrencia,
        UUID idObra,
        UUID idEquipe,
        String statusSeparacao,
        UUID idAlmoxarifadoAtendimento,
        long totalSolicitacoes,
        long totalItens,
        BigDecimal quantidadeSolicitada
) {
}
