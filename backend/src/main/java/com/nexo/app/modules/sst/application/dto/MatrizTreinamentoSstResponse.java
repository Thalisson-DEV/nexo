package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusMatrizTreinamentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.time.LocalDate;
import java.util.UUID;

public record MatrizTreinamentoSstResponse(
        UUID idColaborador,
        String nomeColaborador,
        UUID idFuncao,
        String nomeFuncao,
        UUID idBase,
        String nomeBase,
        UUID idCorpo,
        TipoCorpoGovernancaSst tipoCorpo,
        PapelMembroGovernancaSst papelCorpo,
        UUID idTreinamento,
        String treinamentoCodigo,
        String treinamentoNome,
        UUID idExigencia,
        StatusMatrizTreinamentoSst status,
        LocalDate dataEfetivaVencimento,
        EvidenciaMatrizTreinamentoSst evidencia
) {
}
