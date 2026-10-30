package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.SituacaoVencimento;
import com.nexo.app.modules.frota.domain.TipoDocumentoFrota;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentoFrotaResponse(
        UUID id,
        UUID idVeiculo,
        String placa,
        UUID idImplemento,
        String implemento,
        TipoDocumentoFrota tipo,
        String tipoRotulo,
        String descricao,
        String numero,
        LocalDate dataEmissao,
        LocalDate dataVencimento,
        SituacaoVencimento situacaoVencimento,
        Long diasParaVencer,
        String observacao,
        boolean possuiArquivo,
        String arquivoNome,
        String arquivoContentType,
        Long arquivoTamanhoBytes
) {
}
