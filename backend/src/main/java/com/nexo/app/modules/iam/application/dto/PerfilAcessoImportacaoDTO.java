package com.nexo.app.modules.iam.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record PerfilAcessoImportacaoDTO(
    Integer linha,
    String nome,
    ImportacaoStatus status,
    String mensagemErro
) {
    public PerfilAcessoImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new PerfilAcessoImportacaoDTO(
            linha, nome != null ? nome.toUpperCase() : null, novoStatus, novaMensagem
        );
    }
}
