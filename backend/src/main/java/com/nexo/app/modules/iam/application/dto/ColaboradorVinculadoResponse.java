package com.nexo.app.modules.iam.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * A pessoa por trás do acesso, do ponto de vista de quem administra usuários.
 *
 * <p>Um login como {@code MSOUZA1} só significa alguma coisa para quem criou o login.
 * Este bloco é o que permite à tela dizer "MARIA SOUZA — ELETRICISTA — BASE NORTE" e
 * ser entendida por quem não conhece a convenção de nomes.</p>
 *
 * <p>Vem nulo quando o usuário não tem colaborador vinculado (contas de serviço,
 * integrações, acessos administrativos) — ausência de vínculo é situação legítima,
 * não erro.</p>
 */
@Schema(description = "Colaborador vinculado ao usuário; nulo quando o acesso não pertence a uma pessoa do cadastro de RH")
public record ColaboradorVinculadoResponse(
        UUID id,
        String nomeCompleto,
        String funcao,
        String baseOperacional,

        @Schema(description = "Situação do colaborador no RH — um acesso ativo de alguém já desligado é justamente o que se quer enxergar")
        boolean ativo
) {
}
