package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ContextoCategoriaDocumentoSst;
import com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst;
import com.nexo.app.modules.sst.domain.NaturezaCategoriaDocumentoSst;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.util.List;

public record CategoriaDocumentoSstFilter(
        List<ContextoCategoriaDocumentoSst> contexto,
        List<NaturezaCategoriaDocumentoSst> natureza,
        List<EscopoTitularDocumentoSst> escopo,
        Boolean confirmada,
        Boolean ativa,
        String jurisdicao,
        String buscaGlobal
) {
    public CategoriaDocumentoSstFilter {
        contexto = SelecaoMultipla.normalizar(contexto);
        natureza = SelecaoMultipla.normalizar(natureza);
        escopo = SelecaoMultipla.normalizar(escopo);
    }
}
