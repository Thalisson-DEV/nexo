package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.modules.estoque.domain.CondicaoMaterial;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "DTO de item para importação de movimentações de estoque")
public record MovimentacaoImportacaoDTO(
        @Schema(description = "Número da linha no arquivo", example = "2")
        Integer linha,

        @Schema(description = "Tipo de movimentação (ENTRADA, SAIDA, TRANSFERENCIA)", example = "ENTRADA")
        String tipo,

        @Schema(description = "Nome do estoque de origem", example = "ALMOX CENTRAL")
        String nomeEstoqueOrigem,

        @Schema(description = "ID do estoque de origem", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID idEstoqueOrigem,

        @Schema(description = "Nome do estoque de destino", example = "BASE NORTE")
        String nomeEstoqueDestino,

        @Schema(description = "ID do estoque de destino", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID idEstoqueDestino,

        @Schema(description = "Código do material", example = "MAT-001")
        String codigoMaterial,

        @Schema(description = "ID do material", example = "b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2")
        UUID idMaterial,

        @Schema(description = "Nome do material", example = "CABO COBRE 16MM")
        String nomeMaterial,

        @Schema(description = "Indica se o material requer controle por número de série", example = "false")
        Boolean detalhaSerie,

        @Schema(description = "Quantidade a movimentar", example = "3.000")
        BigDecimal quantidade,

        @Schema(description = "Números de série separados por vírgula, ponto e vírgula, espaço ou barra", example = "SN-001, SN-002, SN-003")
        String numSerie,

        @Schema(description = "Condição do material (NOVO, USADO, RECUPERADO, SUCATA). Padrão: NOVO", example = "NOVO")
        CondicaoMaterial condicao,

        @Schema(description = "Observação adicional", example = "Entrada por compra")
        String observacao,

        @Schema(description = "Status da validação do registro")
        ImportacaoStatus status,

        @Schema(description = "Nome amigável do campo com erro", example = "numSerie")
        String campoErro,

        @Schema(description = "Mensagem de erro ou inconsistência", example = "Material exige número de série.")
        String mensagemErro
) {
    public MovimentacaoImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novoCampoErro, String novaMensagem) {
        return new MovimentacaoImportacaoDTO(
                linha, tipo,
                nomeEstoqueOrigem, idEstoqueOrigem,
                nomeEstoqueDestino, idEstoqueDestino,
                codigoMaterial, idMaterial, nomeMaterial, detalhaSerie,
                quantidade, numSerie, condicao, observacao,
                novoStatus, novoCampoErro, novaMensagem
        );
    }

    public MovimentacaoImportacaoDTO withResolved(
            String tipoNormalizado,
            UUID idOrigem, UUID idDestino,
            UUID idMat, String nomeMat, Boolean detalha,
            BigDecimal qtdAjustada,
            CondicaoMaterial condicaoEfetiva,
            ImportacaoStatus novoStatus, String novoCampoErro, String novaMensagem
    ) {
        return withResolved(
                tipoNormalizado,
                nomeEstoqueOrigem, idOrigem,
                nomeEstoqueDestino, idDestino,
                codigoMaterial, idMat, nomeMat, detalha,
                qtdAjustada, condicaoEfetiva,
                novoStatus, novoCampoErro, novaMensagem
        );
    }

    public MovimentacaoImportacaoDTO withResolved(
            String tipoNormalizado,
            String nomeOrigemResolvido, UUID idOrigem,
            String nomeDestinoResolvido, UUID idDestino,
            String codMatResolvido, UUID idMat, String nomeMat, Boolean detalha,
            BigDecimal qtdAjustada,
            CondicaoMaterial condicaoEfetiva,
            ImportacaoStatus novoStatus, String novoCampoErro, String novaMensagem
    ) {
        return new MovimentacaoImportacaoDTO(
                linha, tipoNormalizado,
                nomeOrigemResolvido != null ? nomeOrigemResolvido : nomeEstoqueOrigem, idOrigem,
                nomeDestinoResolvido != null ? nomeDestinoResolvido : nomeEstoqueDestino, idDestino,
                codMatResolvido != null ? codMatResolvido : codigoMaterial, idMat, nomeMat, detalha,
                qtdAjustada, numSerie, condicaoEfetiva, observacao,
                novoStatus, novoCampoErro, novaMensagem
        );
    }
}

