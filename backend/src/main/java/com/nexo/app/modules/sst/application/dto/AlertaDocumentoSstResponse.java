package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.SituacaoAlertaDocumentoSst;

import java.time.LocalDate;
import java.util.UUID;

public record AlertaDocumentoSstResponse(
        UUID id,
        UUID idDocumento,
        int diasAntecedencia,
        LocalDate dataValidade,
        LocalDate dataAvaliacao,
        SituacaoAlertaDocumentoSst situacao
) {
}
