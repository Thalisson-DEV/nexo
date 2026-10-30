package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.StatusMatrizTreinamentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MatrizTreinamentoSstFilter(
        List<UUID> idBase,
        List<TipoCorpoGovernancaSst> tipoCorpo,
        List<UUID> idCorpo,
        List<UUID> idColaborador,
        List<UUID> idFuncao,
        List<PapelMembroGovernancaSst> papelCorpo,
        List<UUID> idTreinamento,
        List<StatusMatrizTreinamentoSst> status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataCalculo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte
) {
    public MatrizTreinamentoSstFilter {
        idBase = SelecaoMultipla.normalizar(idBase);
        tipoCorpo = SelecaoMultipla.normalizar(tipoCorpo);
        idCorpo = SelecaoMultipla.normalizar(idCorpo);
        idColaborador = SelecaoMultipla.normalizar(idColaborador);
        idFuncao = SelecaoMultipla.normalizar(idFuncao);
        papelCorpo = SelecaoMultipla.normalizar(papelCorpo);
        idTreinamento = SelecaoMultipla.normalizar(idTreinamento);
        status = SelecaoMultipla.normalizar(status);
    }
}
