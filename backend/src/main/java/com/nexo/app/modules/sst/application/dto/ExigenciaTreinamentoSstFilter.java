package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ModoAplicabilidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExigenciaTreinamentoSstFilter(
        List<UUID> idTreinamento,
        List<TipoCorpoGovernancaSst> tipoCorpo,
        List<ModoAplicabilidadeTreinamentoSst> modoAplicabilidade,
        List<UUID> idFuncao,
        List<PapelMembroGovernancaSst> papelCorpo,
        List<UUID> idBase,
        Boolean atual,
        Boolean ativa,
        Boolean fonteConfirmada,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vigenteEm
) {
    public ExigenciaTreinamentoSstFilter {
        idTreinamento = SelecaoMultipla.normalizar(idTreinamento);
        tipoCorpo = SelecaoMultipla.normalizar(tipoCorpo);
        modoAplicabilidade = SelecaoMultipla.normalizar(modoAplicabilidade);
        idFuncao = SelecaoMultipla.normalizar(idFuncao);
        papelCorpo = SelecaoMultipla.normalizar(papelCorpo);
        idBase = SelecaoMultipla.normalizar(idBase);
    }
}
