package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Aceite do documento na conferência contábil.
 *
 * <p>A observação é opcional: a maior parte das conferências não tem nada a dizer,
 * e exigir texto para o caso comum faria a pessoa escrever "ok" mil vezes. Quando
 * há ressalva que não impede o aceite, ela fica registrada aqui.</p>
 */
public record ConferenciaRequest(

        @Schema(description = "Observação da conferência, registrada no documento",
                example = "Nota confere com o boleto; valor divergente em centavos por arredondamento.")
        String observacao
) {}
