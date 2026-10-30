package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.Combustivel;
import com.nexo.app.modules.frota.domain.ProvedorTelemetria;
import com.nexo.app.modules.frota.domain.RegimeVeiculo;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Ficha completa de um veículo, com o que está vigente agora (KM, condutor, implementos). */
public record VeiculoFrotaDetalheResponse(
        UUID idVeiculo,
        String placa,
        String modelo,
        String fabricante,
        UUID idBaseOperacional,
        String baseOperacional,
        TipoVeiculoResponse tipoVeiculo,
        Integer anoFabricacao,
        Integer anoModelo,
        String chassi,
        String renavam,
        String cor,
        Combustivel combustivel,
        BigDecimal capacidadeTanqueLitros,
        BigDecimal consumoReferenciaKmL,
        SituacaoOperacional situacao,
        RegimeVeiculo regime,
        String locadora,
        BigDecimal custoMensalLocacao,
        LocalDate inicioLocacao,
        LocalDate fimLocacao,
        ProvedorTelemetria provedorTelemetria,
        String idRastreador,
        String observacao,
        Long kmAtual,
        OffsetDateTime kmAtualEm,
        VinculoCondutorResponse condutorAtual,
        List<AcoplamentoImplementoResponse> implementosAcoplados,
        long documentosVencidos,
        long documentosAVencer,
        long leiturasInconsistentes,
        boolean ativo
) {
}
