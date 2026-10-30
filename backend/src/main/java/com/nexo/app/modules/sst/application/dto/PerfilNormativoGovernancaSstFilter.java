package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoPerfilNormativoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.time.LocalDate;

public record PerfilNormativoGovernancaSstFilter(
        TipoCorpoGovernancaSst tipoCorpo,
        EscopoPerfilNormativoSst escopo,
        String jurisdicao,
        Boolean confirmado,
        LocalDate vigenteEm,
        String buscaGlobal
) {
}
