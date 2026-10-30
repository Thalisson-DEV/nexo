package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record CentroInvestimentoImportacaoDTO(
    Integer linha,
    String codigo,
    ImportacaoStatus status,
    String mensagemErro
) {
    public CentroInvestimentoImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new CentroInvestimentoImportacaoDTO(
            linha, 
            codigo != null ? codigo.toUpperCase() : null, 
            novoStatus, 
            novaMensagem
        );
    }
}
