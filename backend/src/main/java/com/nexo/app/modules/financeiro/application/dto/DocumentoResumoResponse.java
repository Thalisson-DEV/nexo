package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Documento visto de dentro da solicitação. Inclui os dados operacionais de
 * pagamento, duplicidade, conferência e as ações disponíveis para que a tela não
 * precise buscar cada anexo separadamente.
 */
@Schema(description = "Documento vinculado a uma solicitação")
public record DocumentoResumoResponse(
        UUID id,
        String nomeArquivo,
        String contentType,
        Long tamanhoBytes,
        TipoDocumentoFinanceiro tipo,
        String tipoRotulo,
        SituacaoDocumento situacao,
        String situacaoRotulo,
        String numeroDocumento,
        String chaveAcesso,
        String codigoPagamento,
        boolean possuiCodigoPagamento,
        boolean duplicado,
        boolean identificacaoPendente,
        BigDecimal valor,
        UUID usuarioConferenciaId,
        OffsetDateTime dataConferencia,
        String observacaoConferencia,
        String motivoPendencia,
        AcoesDocumentoResponse acoes,
        OffsetDateTime dataCriacao,
        String usuarioCriacao
) {}
