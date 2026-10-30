package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Solicitação de pagamento pronta para consumo direto pela tela, incluindo a
 * compra de origem quando houver.
 */
@Schema(description = "Solicitação de pagamento")
public record SolicitacaoPagamentoResponse(

        UUID id,

        @Schema(description = "Número sequencial legível", example = "507")
        Long numero,

        StatusSolicitacao status,

        FormularioResumoResponse formulario,

        @Schema(description = "Valores preenchidos, indexados pela chave do campo")
        Map<String, CampoValorResponse> valores,

        @Schema(description = "Valor solicitado")
        BigDecimal valor,

        FornecedorSolicitacaoResponse fornecedor,

        @Schema(description = "Compra de origem, quando o pagamento estiver vinculado")
        SolicitacaoCompraResumoResponse compraVinculada,

        UUID usuarioSolicitanteId,

        @Schema(description = "Login de quem abriu a solicitação, já resolvido")
        String usuarioSolicitante,

        @Schema(description = "Nome completo do colaborador vinculado a quem abriu a solicitação")
        String nomeColaboradorSolicitante,

        OffsetDateTime dataSubmissao,

        DesfechoResponse desfecho,

        @Schema(description = "Documentos vinculados, no repositório financeiro")
        List<DocumentoResumoResponse> documentos,

        AcoesDisponiveisResponse acoes,

        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao,
        String usuarioUltimaAlteracao
) {}
