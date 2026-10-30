package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Solicitação de compra pronta para consumo direto pela tela.
 *
 * <p>Os campos preenchidos vêm em {@code valores}, indexados pela mesma chave do
 * schema e já com o texto legível resolvido — o frontend renderiza a listagem e o
 * detalhe sem conhecer os campos de antemão e sem buscar cada referência.</p>
 */
@Schema(description = "Solicitação de compra")
public record SolicitacaoCompraResponse(

        UUID id,

        @Schema(description = "Número sequencial legível", example = "1042")
        Long numero,

        StatusSolicitacao status,

        @Schema(description = "Variante de formulário utilizada")
        FormularioResumoResponse formulario,

        @Schema(description = "Valores preenchidos, indexados pela chave do campo")
        Map<String, CampoValorResponse> valores,

        @Schema(description = "Valor solicitado")
        BigDecimal valor,

        FornecedorSolicitacaoResponse fornecedor,

        UUID usuarioSolicitanteId,

        @Schema(description = "Login de quem abriu a solicitação, já resolvido")
        String usuarioSolicitante,

        @Schema(description = "Nome completo do colaborador vinculado a quem abriu a solicitação")
        String nomeColaboradorSolicitante,

        OffsetDateTime dataSubmissao,

        DesfechoResponse desfecho,

        @Schema(description = "Documentos vinculados, no repositório financeiro")
        List<DocumentoResumoResponse> documentos,

        @Schema(description = "Ações habilitadas para o usuário atual")
        AcoesDisponiveisResponse acoes,

        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao,
        String usuarioUltimaAlteracao
) {}
