package com.nexo.app.modules.automacao.application.dto;

import com.nexo.app.modules.automacao.domain.OrigemAutomacao;
import com.nexo.app.modules.automacao.domain.StatusAutomacao;
import com.nexo.app.modules.automacao.domain.TipoAutomacao;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Uma automação na listagem. */
public record AutomacaoResumoResponse(
        UUID id,
        String nome,
        String descricao,
        TipoAutomacao tipo,
        StatusAutomacao status,
        OrigemAutomacao origem,
        String gatilhoTipo,
        String gatilhoRotulo,
        String gatilhoResumo,
        OffsetDateTime proximaExecucao,
        UltimaExecucaoResponse ultimaExecucao,
        int execucoes30Dias,
        int falhas30Dias,
        UUID donoId,
        String donoNome,
        int numeroVersao,
        OffsetDateTime dataUltimaAlteracao
) {
}
