package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record TipoEstoqueImportacaoDTO(
    Integer linha,
    String nome,
    ImportacaoStatus status,
    String mensagemErro
) {
    public TipoEstoqueImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new TipoEstoqueImportacaoDTO(
            linha, 
            nome != null ? nome.toUpperCase() : null, 
            novoStatus, 
            novaMensagem
        );
    }
}
