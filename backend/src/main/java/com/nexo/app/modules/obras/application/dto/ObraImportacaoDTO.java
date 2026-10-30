package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record ObraImportacaoDTO(
        Integer linha,
        String numero,
        String titulo,
        String descricao,
        String notaObra,
        String valorProjeto,
        String dataInicio,
        String dataPrevisaoFim,
        String latitude,
        String longitude,
        String nomeProcesso,
        String nomeBaseOperacional,
        String nomeMunicipio,
        String nomeCentroCusto,
        String nomeSupervisor,
        String nomeCoordenador,
        String nomeStatus,
        String nomeAgenteResponsavel,
        String codigosEquipes,
        String tipoObra,
        ImportacaoStatus status,
        String mensagemErro
) {
    public ObraImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new ObraImportacaoDTO(
                linha, numero, titulo, descricao, notaObra, valorProjeto,
                dataInicio, dataPrevisaoFim, latitude, longitude,
                nomeProcesso, nomeBaseOperacional, nomeMunicipio, nomeCentroCusto,
                nomeSupervisor, nomeCoordenador,
                nomeStatus, nomeAgenteResponsavel, codigosEquipes,
                tipoObra,
                novoStatus, novaMensagem
        );
    }
}
