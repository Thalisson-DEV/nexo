package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record CoordenadorImportacaoDTO(
    Integer linha,
    String cpfColaborador,
    String nomeColaborador,
    String dataInicio,
    String dataFim,
    ImportacaoStatus status,
    String mensagemErro
) {
    public CoordenadorImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new CoordenadorImportacaoDTO(
            linha, cpfColaborador, nomeColaborador, dataInicio, dataFim,
            novoStatus, novaMensagem
        );
    }
}
