package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.RegimeVeiculo;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.util.List;
import java.util.UUID;

/** Filtro da listagem de veículos da frota. {@code busca} procura em placa, chassi e renavam. */
public record FichaVeiculoFilter(
        String busca,
        List<UUID> idsBase,
        List<UUID> idsTipoVeiculo,
        List<SituacaoOperacional> situacoes,
        RegimeVeiculo regime,
        Boolean ativo
) {
    public FichaVeiculoFilter {
        idsBase = SelecaoMultipla.normalizar(idsBase);
        idsTipoVeiculo = SelecaoMultipla.normalizar(idsTipoVeiculo);
        situacoes = SelecaoMultipla.normalizar(situacoes);
    }
}
