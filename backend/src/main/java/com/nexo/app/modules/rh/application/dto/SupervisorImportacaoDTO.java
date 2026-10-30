package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record SupervisorImportacaoDTO(
    Integer linha,
    String cpfColaborador,
    String nomeColaborador,
    String cpfCoordenador,
    String nomeCoordenador,
    String dataInicio,
    String dataFim,
    ImportacaoStatus status,
    String mensagemErro
) {
    public SupervisorImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new SupervisorImportacaoDTO(
            linha, cpfColaborador, nomeColaborador, cpfCoordenador, nomeCoordenador,
            dataInicio, dataFim, novoStatus, novaMensagem
        );
    }
}
