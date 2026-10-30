package com.nexo.app.shared.domain.events;

import java.util.UUID;

/**
 * Placa, base ou situação de um veículo mudaram no cadastro do RH.
 *
 * <p>Mantém atualizada a projeção que a frota guarda na ficha do veículo.</p>
 */
public record VeiculoAlteradoEvent(UUID idVeiculo, String placa, UUID idBaseOperacional, boolean ativo) {
}
