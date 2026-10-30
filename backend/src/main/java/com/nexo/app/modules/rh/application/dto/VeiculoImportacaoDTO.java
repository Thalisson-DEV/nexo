package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record VeiculoImportacaoDTO(
    Integer linha,
    String placa,
    String modelo,
    String fabricante,
    String nomeBaseOperacional,
    ImportacaoStatus status,
    String mensagemErro
) {
    public VeiculoImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new VeiculoImportacaoDTO(
            linha, placa.toUpperCase().trim(), modelo.toUpperCase().trim(), 
            fabricante != null ? fabricante.toUpperCase().trim() : null,
            nomeBaseOperacional != null ? nomeBaseOperacional.toUpperCase().trim() : null,
            novoStatus, novaMensagem
        );
    }
}
