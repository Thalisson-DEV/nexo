package com.nexo.app.modules.execucao;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Identificação de uma nota para quem só precisa citá-la (ex.: em qual serviço um material
 * foi aplicado), sem hidratar equipe, ações e materiais.
 */
public record NotaResumida(
        UUID idExecucao,
        String referenciaNota,
        /* Nulo nas execuções sem tipo de serviço informado. */
        String tipoServico,
        OffsetDateTime dataLancamento
) {
}
