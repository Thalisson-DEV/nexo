package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.Combustivel;
import com.nexo.app.modules.frota.domain.RegimeVeiculo;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Linha da listagem de veículos da frota. */
public record VeiculoFrotaResumoResponse(
        UUID idVeiculo,
        String placa,
        String modelo,
        String fabricante,
        UUID idBaseOperacional,
        String baseOperacional,
        UUID idTipoVeiculo,
        String tipoVeiculo,
        SituacaoOperacional situacao,
        RegimeVeiculo regime,
        Combustivel combustivel,
        Integer anoModelo,
        Long kmAtual,
        OffsetDateTime kmAtualEm,
        UUID idCondutorAtual,
        String condutorAtual,
        List<String> implementosAcoplados,
        long documentosVencidos,
        long documentosAVencer,
        long leiturasInconsistentes,
        boolean ativo
) {
}
