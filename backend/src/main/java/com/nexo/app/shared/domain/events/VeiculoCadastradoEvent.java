package com.nexo.app.shared.domain.events;

import java.util.UUID;

/**
 * Um veículo passou a existir no cadastro do RH.
 *
 * <p>Publicado por toda porta de cadastro (tela, importação e API pública) para que
 * a frota crie a ficha do veículo sem que o RH conheça o módulo de frota. Leva a
 * placa e a base porque a frota guarda uma projeção delas para filtrar e ordenar.</p>
 */
public record VeiculoCadastradoEvent(UUID idVeiculo, String placa, UUID idBaseOperacional) {
}
