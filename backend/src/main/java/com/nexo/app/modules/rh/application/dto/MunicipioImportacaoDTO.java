package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record MunicipioImportacaoDTO(
    Integer linha,
    String nome,
    String nomeBaseOperacional,
    ImportacaoStatus status,
    String mensagemErro
) {
    public MunicipioImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new MunicipioImportacaoDTO(
            linha, 
            nome != null ? nome.toUpperCase() : null, 
            nomeBaseOperacional != null ? nomeBaseOperacional.toUpperCase() : null, 
            novoStatus, 
            novaMensagem
        );
    }
}
