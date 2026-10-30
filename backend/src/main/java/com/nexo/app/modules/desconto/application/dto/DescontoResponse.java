package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.StatusDesconto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DescontoResponse(
        UUID id,
        UUID colaboradorId,
        MotivoDescontoResponse motivo,
        String observacoes,
        Integer quantidadeParcelas,
        BigDecimal valorSugerido,
        BigDecimal valorValidado,
        BigDecimal valorBaixado,
        StatusDesconto status,
        UUID usuarioSolicitanteId,
        UUID usuarioValidadorId,
        UUID usuarioBaixaId,
        OffsetDateTime dataOcorrencia,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataValidacao,
        OffsetDateTime dataBaixa,
        String motivoRejeicao,
        UUID usuarioRejeicaoId,
        OffsetDateTime dataRejeicao,
        String usuarioUltimaAlteracao,
        OffsetDateTime dataUltimaAlteracao,
        List<DescontoItemResponse> itens,
        List<DescontoAnexoResponse> anexos
) {}
