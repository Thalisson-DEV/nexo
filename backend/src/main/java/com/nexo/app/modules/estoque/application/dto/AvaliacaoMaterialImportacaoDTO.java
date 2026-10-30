package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record AvaliacaoMaterialImportacaoDTO(
    Integer linha,
    String nome,
    ImportacaoStatus status,
    String mensagemErro
) {
    public AvaliacaoMaterialImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new AvaliacaoMaterialImportacaoDTO(
            linha, 
            nome != null ? nome.toUpperCase() : null, 
            novoStatus, 
            novaMensagem
        );
    }
}
