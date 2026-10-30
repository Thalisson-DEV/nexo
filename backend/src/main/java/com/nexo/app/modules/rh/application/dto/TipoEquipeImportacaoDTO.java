package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record TipoEquipeImportacaoDTO(
    Integer linha,
    String nome,
    ImportacaoStatus status,
    String mensagemErro
) {
    public TipoEquipeImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new TipoEquipeImportacaoDTO(
            linha, nome != null ? nome.toUpperCase() : null, novoStatus, novaMensagem
        );
    }
}
