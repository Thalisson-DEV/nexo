package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.RegimeComposicaoSst;
import com.nexo.app.modules.sst.domain.StatusCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CorpoGovernancaSstFilter(
        List<TipoCorpoGovernancaSst> tipo,
        List<StatusCorpoGovernancaSst> status,
        List<RegimeComposicaoSst> regimeComposicao,
        List<UUID> idBaseOperacional,
        UUID idPerfilNormativo,
        Boolean conformidadeNormativaValidada,
        LocalDate periodoDe,
        LocalDate periodoAte,
        String buscaGlobal
) {
    public CorpoGovernancaSstFilter {
        tipo = SelecaoMultipla.normalizar(tipo);
        status = SelecaoMultipla.normalizar(status);
        regimeComposicao = SelecaoMultipla.normalizar(regimeComposicao);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
    }
}
