package com.nexo.app.modules.sst.application.dto;

import java.time.LocalDate;

public record ResumoMatrizTreinamentoSstResponse(
        long populacaoAvaliada,
        long regulares,
        long aVencer,
        long vencidos,
        long ausentes,
        long incompativeis,
        LocalDate dataCalculo,
        MatrizTreinamentoSstFilter filtros
) {
    public ResumoMatrizTreinamentoSstResponse {
        long soma = regulares + aVencer + vencidos + ausentes + incompativeis;
        if (populacaoAvaliada != soma) {
            throw new IllegalArgumentException(
                    "A soma das situações deve corresponder à população avaliada.");
        }
    }
}
