package com.nexo.app.shared.domain.events;

import com.nexo.app.shared.application.dto.OperacaoMaterial;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Evento disparado quando uma execução comercial é concluída,
 * permitindo que outros módulos (como Logística) processem os materiais.
 */
public record ExecucaoConcluidaEvent(
    UUID idExecucao,
    UUID idEquipe,
    UUID idUsuarioOperador,
    List<ItemMaterialEvent> itens
) {
    public record ItemMaterialEvent(
        UUID idMaterial,
        BigDecimal quantidade,
        OperacaoMaterial operacao,
        String numSerie
    ) {}
}
