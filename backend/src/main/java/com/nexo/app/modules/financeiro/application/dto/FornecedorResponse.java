package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.StatusFornecedor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fornecedor retornado nas listagens e consultas.
 *
 * <p>{@code utilizavel} evita que o frontend replique a regra de quais status
 * aceitam novas solicitações — basta desabilitar a opção quando vier {@code false}.</p>
 */
public record FornecedorResponse(
        UUID id,
        String nomeFantasia,
        String razaoSocial,
        String documento,
        String dadosBancarios,
        String observacoes,
        StatusFornecedor status,
        String motivoBloqueio,
        boolean utilizavel,
        boolean ativo,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataUltimaAlteracao,
        String usuarioUltimaAlteracao
) {}
