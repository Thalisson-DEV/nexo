package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Identificacao segura do fornecedor exibida nas listagens de solicitacoes. */
@Schema(description = "Fornecedor cadastrado ou digitado na solicitacao")
public record FornecedorSolicitacaoResponse(
        UUID id,
        String nome,
        String razaoSocial,
        boolean cadastrado
) {}
