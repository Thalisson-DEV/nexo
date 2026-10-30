package com.nexo.app.modules.sst.application.dto;

import java.time.LocalDate;

public record ResumoDocumentosSstResponse(
        long total,
        long regulares,
        long aVencer,
        long vencidos,
        long semValidade,
        long pendentesConferencia,
        long rejeitados,
        LocalDate dataCalculo
) {
}
