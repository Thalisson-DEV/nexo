package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.SituacaoVencimento;
import com.nexo.app.modules.frota.domain.TipoDocumentoFrota;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentoFrotaFilter(
        String busca,
        UUID idVeiculo,
        UUID idImplemento,
        List<UUID> idsBase,
        List<TipoDocumentoFrota> tipos,
        SituacaoVencimento situacao,
        LocalDate vencimentoAte
) {
    public DocumentoFrotaFilter {
        idsBase = SelecaoMultipla.normalizar(idsBase);
        tipos = SelecaoMultipla.normalizar(tipos);
    }
}
