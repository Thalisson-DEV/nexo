package com.nexo.app.modules.estoque.application.dto;

import java.util.List;
import java.util.UUID;

public record ConfirmacaoImportacaoSolicitacaoTecnicaResponse(
        UUID idLote,
        List<Resultado> resultados,
        ProcessamentoImportacao processamento
) {
    public ConfirmacaoImportacaoSolicitacaoTecnicaResponse(UUID idLote, List<Resultado> resultados) {
        this(idLote, resultados, null);
    }
    public ConfirmacaoImportacaoSolicitacaoTecnicaResponse {
        resultados = resultados == null ? List.of() : List.copyOf(resultados);
    }

    public record Resultado(
            String chave,
            String tipo,
            List<PreviaImportacaoSolicitacaoTecnica.SolicitacaoCriada> solicitacoes,
            String erro
    ) {
        public Resultado {
            solicitacoes = solicitacoes == null ? List.of() : List.copyOf(solicitacoes);
        }

        public boolean sucesso() {
            return erro == null;
        }

    }
}
