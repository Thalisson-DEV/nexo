package com.nexo.app.modules.automacao.application.dto;

import java.util.List;
import java.util.Map;

/** Execução completa: a versão que rodou, a saída do gatilho e cada etapa. */
public record ExecucaoDetalheResponse(
        ExecucaoResumoResponse resumo,
        String automacaoNome,
        Map<String, Object> definicao,
        Map<String, Object> saidaGatilho,
        List<EtapaResponse> etapas,
        List<AprovacaoResponse> aprovacoes,
        List<ArquivoExecucaoResponse> arquivos
) {
}
