package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record EquipeImportacaoDTO(
    Integer linha,
    String codigo,
    String nomeTipoEquipe,
    String nomeBase,
    String placaVeiculo,
    String nomeCoordenador,
    String nomeSupervisor,
    String nomeResponsavel,
    String nomeProcesso,
    ImportacaoStatus status,
    String mensagemErro
) {
    public EquipeImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new EquipeImportacaoDTO(
            linha, codigo, nomeTipoEquipe, nomeBase, placaVeiculo,
                nomeCoordenador, nomeSupervisor, nomeResponsavel, nomeProcesso,
            novoStatus, novaMensagem
        );
    }
}