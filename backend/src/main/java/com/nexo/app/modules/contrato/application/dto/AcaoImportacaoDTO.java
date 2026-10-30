package com.nexo.app.modules.contrato.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record AcaoImportacaoDTO(
    Integer linha,
    String codigo,
    String nome,
    ImportacaoStatus status,
    String mensagemErro
) {
    public AcaoImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new AcaoImportacaoDTO(
            linha,
            codigo != null ? codigo.toUpperCase() : null,
            nome != null ? nome.toUpperCase() : null,
            novoStatus,
            novaMensagem
        );
    }
}
