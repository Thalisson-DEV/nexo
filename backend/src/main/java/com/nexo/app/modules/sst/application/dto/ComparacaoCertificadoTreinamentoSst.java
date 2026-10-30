package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.MotivoIncompatibilidadeTreinamentoSst;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ComparacaoCertificadoTreinamentoSst(
        UUID idDocumento,
        boolean cargaHorariaAtendida,
        boolean instituicaoAtendida,
        boolean modalidadeAtendida,
        Set<MotivoIncompatibilidadeTreinamentoSst> incompatibilidades,
        LocalDate dataConclusao,
        LocalDate validadeDeclarada,
        LocalDate dataRecorrencia,
        LocalDate dataEfetivaVencimento,
        boolean selecionado
) {
}
