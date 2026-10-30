package com.nexo.app.modules.estoque.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record MaterialImportacaoDTO(
    Integer linha,
    String codigo,
    String descricao,
    String unidadeMedida,
    String suprMatrFlag,
    String nomeProcesso,
    String nomeAvaliacao,
    String codigoCentroInvestimento,
    boolean detalhaSerie,
    ImportacaoStatus status,
    String mensagemErro
) {
    public MaterialImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new MaterialImportacaoDTO(
            linha, codigo, descricao, unidadeMedida, suprMatrFlag, 
            nomeProcesso, nomeAvaliacao, codigoCentroInvestimento, detalhaSerie,
            novoStatus, novaMensagem
        );
    }
}
