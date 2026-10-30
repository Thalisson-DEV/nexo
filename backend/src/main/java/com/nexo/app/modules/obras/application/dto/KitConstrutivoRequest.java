package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.ClasseTensao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro/edição de kit. Na edição, {@code itens} substitui a lista de itens ativos: itens com {@code id}
 * são alterados, sem {@code id} são criados e os ausentes são desativados.
 */
public record KitConstrutivoRequest(
        @NotNull(message = "O contrato do kit é obrigatório")
        UUID idContrato,

        @NotBlank(message = "O código do kit é obrigatório")
        @Size(max = 40, message = "O código do kit deve ter até 40 caracteres")
        String codigo,

        @NotBlank(message = "A descrição do kit é obrigatória")
        @Size(max = 300, message = "A descrição do kit deve ter até 300 caracteres")
        String descricao,

        @Size(max = 60, message = "O grupo deve ter até 60 caracteres")
        String grupo,

        Set<ClasseTensao> classesTensao,

        @Valid
        List<ItemKitRequest> itens
) {
}
