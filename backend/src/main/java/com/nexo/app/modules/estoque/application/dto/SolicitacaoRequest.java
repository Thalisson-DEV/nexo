package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados para criação de uma nova solicitação de material")
public record SolicitacaoRequest(
    @Schema(description = "ID do estoque de onde o material será retirado", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idEstoqueOrigem,

    @Schema(description = "ID legado do estoque de destino. Quando informado, deve coincidir com o estoque CAMPO da equipe")
    UUID idEstoqueDestino,

    @NotNull
    @Schema(description = "Data prevista para o saque. O prazo recomendado e de 48 horas; menor antecedencia e apenas sinalizada", requiredMode = Schema.RequiredMode.REQUIRED)
    LocalDate dataPrevista,

    @NotNull(message = "A equipe responsavel pelo saque e obrigatoria")
    @Schema(description = "Equipe responsavel pelo saque e dona do estoque de destino", requiredMode = Schema.RequiredMode.REQUIRED)
    UUID idEquipe,

    @Schema(description = "Colaborador inicialmente previsto para realizar o saque")
    UUID idColaboradorSaque,

    @Schema(description = "Indica que o colaborador previsto nao pertence a equipe de saque")
    Boolean saqueForaEquipe,

    @Schema(description = "Lista de materiais e quantidades solicitadas")
    List<ItemRequest> itens,

    @Schema(description = "Observacao opcional da solicitacao", maxLength = 1000)
    String observacao
) {
    public SolicitacaoRequest(UUID idEstoqueOrigem, UUID idEstoqueDestino, LocalDate dataPrevista,
            UUID idEquipe, UUID idColaboradorSaque, Boolean saqueForaEquipe, List<ItemRequest> itens) {
        this(idEstoqueOrigem, idEstoqueDestino, dataPrevista, idEquipe,
                idColaboradorSaque, saqueForaEquipe, itens, null);
    }
    public SolicitacaoRequest(UUID idEstoqueOrigem, UUID idEstoqueDestino, List<ItemRequest> itens) {
        this(idEstoqueOrigem, idEstoqueDestino, null, null, null, false, itens, null);
    }

    public SolicitacaoRequest(
            UUID idEstoqueOrigem, UUID idEstoqueDestino, LocalDate dataPrevista, List<ItemRequest> itens) {
        this(idEstoqueOrigem, idEstoqueDestino, dataPrevista, null, null, false, itens, null);
    }

    @Schema(description = "Item específico da solicitação")
    public record ItemRequest(
        @Schema(description = "ID do material cadastrado no sistema", example = "b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID idMaterial,

        @Schema(description = "Quantidade solicitada (até 3 casas decimais)", example = "10.500", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal quantidade
    ) {}
}
