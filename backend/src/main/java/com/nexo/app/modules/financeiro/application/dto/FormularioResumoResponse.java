package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;

import java.util.UUID;

/**
 * Identificação enxuta de uma variante, para listagens de escolha e para embutir
 * na resposta de uma solicitação sem arrastar o schema inteiro.
 */
public record FormularioResumoResponse(
        UUID id,
        TipoSolicitacao tipoSolicitacao,
        String codigo,
        String titulo,
        String descricao,
        boolean padrao,
        boolean ativo
) {}
