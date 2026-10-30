package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record FuncaoImportacaoDTO(
    Integer linha,
    String nome,
    String descricao,
    ImportacaoStatus status,
    String mensagemErro
) {
    public FuncaoImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new FuncaoImportacaoDTO(
            linha, nome != null ? nome.toUpperCase() : null, descricao, novoStatus, novaMensagem
        );
    }
}
