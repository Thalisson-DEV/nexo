package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "DTO de item para importação de solicitações comerciais de material")
public record SolicitacaoComercialImportacaoDTO(
        @Schema(description = "Número da linha no arquivo original", example = "2")
        Integer linha,

        @Schema(description = "Nome do estoque de origem", example = "ALMOX CENTRAL")
        String nomeEstoqueOrigem,

        @Schema(description = "ID do estoque de origem", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID idEstoqueOrigem,

        @Schema(description = "Nome do estoque de destino", example = "EQ-01")
        String nomeEstoqueDestino,

        @Schema(description = "ID do estoque de destino", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID idEstoqueDestino,

        @Schema(description = "Campo legado ignorado; o requisitante é a pessoa que confirma a importação", example = "JOÃO SILVA")
        String nomeRequisitante,

        @Schema(description = "Campo legado ignorado; o requisitante é a pessoa que confirma a importação", example = "c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22")
        UUID idRequisitante,

        @Schema(description = "Código do material", example = "MAT-001")
        String codigoMaterial,

        @Schema(description = "ID do material", example = "b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2")
        UUID idMaterial,

        @Schema(description = "Descrição/Nome do material", example = "CABO COBRE 16MM")
        String nomeMaterial,

        @Schema(description = "Quantidade solicitada", example = "10.500")
        BigDecimal quantidade,

        @Schema(description = "Data prevista obrigatoria para o saque", example = "2026-09-25")
        LocalDate dataPrevista,

        @Schema(description = "Observação do pedido", example = "Atendimento mensal de rotina")
        String observacao,

        @Schema(description = "Status da validação do registro")
        ImportacaoStatus status,

        @Schema(description = "Mensagem de erro ou inconsistência da linha", example = "Material não encontrado")
        String mensagemErro
) {
    public SolicitacaoComercialImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new SolicitacaoComercialImportacaoDTO(
                linha,
                nomeEstoqueOrigem, idEstoqueOrigem,
                nomeEstoqueDestino, idEstoqueDestino,
                nomeRequisitante, idRequisitante,
                codigoMaterial, idMaterial, nomeMaterial,
                quantidade, dataPrevista, observacao, novoStatus, novaMensagem
        );
    }

    public SolicitacaoComercialImportacaoDTO withResolved(
            UUID idEstoqueOrigem, UUID idEstoqueDestino, UUID idMaterial, String nomeMaterial,
            ImportacaoStatus status, String mensagemErro
    ) {
        return new SolicitacaoComercialImportacaoDTO(
                linha,
                nomeEstoqueOrigem, idEstoqueOrigem,
                nomeEstoqueDestino, idEstoqueDestino,
                null, null,
                codigoMaterial, idMaterial, nomeMaterial,
                quantidade, dataPrevista, observacao, status, mensagemErro
        );
    }
}
