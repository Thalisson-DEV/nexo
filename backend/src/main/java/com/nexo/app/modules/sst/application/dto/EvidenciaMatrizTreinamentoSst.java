package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ModoAplicabilidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusMatrizTreinamentoSst;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EvidenciaMatrizTreinamentoSst(
        LocalDate dataCalculo,
        UUID idExigencia,
        int revisaoExigencia,
        ModoAplicabilidadeTreinamentoSst modoAplicabilidade,
        UUID idFuncaoAvaliada,
        PapelMembroGovernancaSst papelAvaliado,
        List<UUID> idsCertificadosConsiderados,
        UUID idCertificadoSelecionado,
        List<ComparacaoCertificadoTreinamentoSst> comparacoes,
        LocalDate dataEfetivaVencimento,
        StatusMatrizTreinamentoSst status,
        String motivoStatus
) {
}
