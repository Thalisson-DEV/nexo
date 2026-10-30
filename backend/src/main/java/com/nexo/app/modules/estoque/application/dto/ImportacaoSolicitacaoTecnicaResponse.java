package com.nexo.app.modules.estoque.application.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ImportacaoSolicitacaoTecnicaResponse(
        String token,
        String hashArquivo,
        OffsetDateTime expiraEm,
        int totalLinhas,
        int totalSolicitacoesPrevistas,
        int totalConfirmaveis,
        int totalErros,
        int totalAvisos,
        List<PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista> solicitacoesPrevistas,
        ProcessamentoImportacao processamento
) {
    public ImportacaoSolicitacaoTecnicaResponse(
            String token, String hashArquivo, OffsetDateTime expiraEm,
            int totalLinhas, int totalSolicitacoesPrevistas, int totalConfirmaveis,
            int totalErros, int totalAvisos,
            List<PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista> solicitacoesPrevistas) {
        this(token, hashArquivo, expiraEm, totalLinhas, totalSolicitacoesPrevistas,
                totalConfirmaveis, totalErros, totalAvisos, solicitacoesPrevistas, null);
    }
    public ImportacaoSolicitacaoTecnicaResponse {
        solicitacoesPrevistas = solicitacoesPrevistas == null ? List.of() : List.copyOf(solicitacoesPrevistas);
    }
}
