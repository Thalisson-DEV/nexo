package com.nexo.app.modules.frota.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Valores da ficha que o gestor edita de uma vez. */
public record DadosFichaVeiculo(
        TipoVeiculo tipoVeiculo,
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
        String observacao
) {
}
