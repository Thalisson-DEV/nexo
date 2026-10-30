package com.nexo.app.modules.estoque.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Dados de retorno de um Motivo de Rejeição")
public record MotivoRejeicaoResponse(
        @Schema(description = "ID único", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,
        
        @Schema(description = "Nome curto do motivo", example = "Falta de Estoque")
        String nome,
        
        @Schema(description = "Descrição detalhada", example = "Material não disponível no almoxarifado no momento.")
        String descricao,
        
        @Schema(description = "Status do registro", example = "true")
        boolean ativo,
        
        @Schema(description = "Data de criação")
        OffsetDateTime dataCriacao,
        
        @Schema(description = "Usuário criador", example = "admin_user")
        String usuarioCriacao
) {}
