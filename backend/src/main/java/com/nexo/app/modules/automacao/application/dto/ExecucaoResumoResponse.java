package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.ModoExecucao;
import com.nexo.app.modules.automacao.domain.StatusExecucao;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Uma execução na lista do histórico. */
public record ExecucaoResumoResponse(
        UUID id,
        UUID automacaoId,
        StatusExecucao status,
        ModoExecucao modo,
        int numeroVersao,
        OffsetDateTime criadaEm,
        OffsetDateTime iniciadaEm,
        OffsetDateTime finalizadaEm,
        Long duracaoMs,
        String erro,
        String mensagem,
        String aguardando,
        OffsetDateTime retomarEm,
        String iniciadaPor,
        int passos
) {
}
