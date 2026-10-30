package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.Combustivel;
import com.nexo.app.modules.frota.domain.ProvedorTelemetria;
import com.nexo.app.modules.frota.domain.RegimeVeiculo;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Cadastro completo do veículo: dados do RH (placa, modelo, fabricante, base) e ficha da frota. */
public record VeiculoFrotaRequest(
        @NotBlank(message = "Informe a placa.") @Size(min = 3, max = 10) String placa,
        @NotBlank(message = "Informe o modelo.") @Size(min = 3, max = 100) String modelo,
        @NotBlank(message = "Informe a marca/fabricante.") @Size(min = 3, max = 50) String fabricante,
        @NotNull(message = "Informe a base operacional.") UUID idBaseOperacional,
        UUID idTipoVeiculo,
        Integer anoFabricacao,
        Integer anoModelo,
        @Size(max = 30) String chassi,
        @Size(max = 20) String renavam,
        @Size(max = 40) String cor,
        Combustivel combustivel,
        @DecimalMin("0") BigDecimal capacidadeTanqueLitros,
        @DecimalMin("0") BigDecimal consumoReferenciaKmL,
        SituacaoOperacional situacao,
        RegimeVeiculo regime,
        @Size(max = 150) String locadora,
        @DecimalMin("0") BigDecimal custoMensalLocacao,
        LocalDate inicioLocacao,
        LocalDate fimLocacao,
        ProvedorTelemetria provedorTelemetria,
        @Size(max = 80) String idRastreador,
        @Size(max = 1000) String observacao
) {
}
