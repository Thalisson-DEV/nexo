package com.nexo.app.modules.sst.domain;

import java.util.UUID;

/**
 * Onde o evento aconteceu, na estrutura da operação, <b>como estava</b> no registro:
 * base operacional, equipe e veículo.
 *
 * <p>Os identificadores servem para agrupar e filtrar; o texto ao lado serve para
 * exibir. Uma equipe renomeada continua aparecendo com o código antigo nos eventos
 * antigos — isso é a intenção, não um defeito, e precisa estar dito na interface.</p>
 *
 * <p>A base é obrigatória: é ela que define o recorte organizacional de quem pode ler
 * o evento e o denominador de "dias sem acidente". Equipe e veículo são opcionais —
 * nem todo evento acontece com uma equipe montada ou dentro de um carro.</p>
 */
public record SnapshotOperacional(
        UUID idBaseOperacional,
        String baseNome,
        UUID idEquipe,
        String equipeCodigo,
        UUID idVeiculo,
        String veiculoPlaca
) {
}
