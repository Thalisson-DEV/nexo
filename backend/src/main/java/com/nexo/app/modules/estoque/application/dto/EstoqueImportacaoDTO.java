package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record EstoqueImportacaoDTO(
    Integer linha,
    String nomeIdentificador,
    String nomeTipoEstoque,
    String codigoEquipe,
    ImportacaoStatus status,
    String mensagemErro
) {
    public EstoqueImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new EstoqueImportacaoDTO(
            linha, nomeIdentificador, nomeTipoEstoque, codigoEquipe, 
            novoStatus, novaMensagem
        );
    }
}
