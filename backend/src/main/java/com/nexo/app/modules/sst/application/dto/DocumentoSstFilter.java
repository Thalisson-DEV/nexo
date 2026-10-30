package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst;
import com.nexo.app.modules.sst.domain.SituacaoConferenciaDocumentoSst;
import com.nexo.app.modules.sst.domain.StatusValidadeDocumentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentoSstFilter(
        List<UUID> idBase,
        List<TipoCorpoGovernancaSst> contexto,
        List<UUID> idCategoria,
        List<EscopoTitularDocumentoSst> escopoTitular,
        List<UUID> idTitular,
        List<SituacaoConferenciaDocumentoSst> situacaoConferencia,
        List<StatusValidadeDocumentoSst> statusValidade,
        UUID idColaborador,
        UUID idTreinamento,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate emissaoDe,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate emissaoAte,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validadeDe,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validadeAte,
        Integer venceEmDias,
        Boolean atual,
        Boolean arquivado,
        String buscaGlobal
) {
    public DocumentoSstFilter {
        idBase = SelecaoMultipla.normalizar(idBase);
        contexto = SelecaoMultipla.normalizar(contexto);
        idCategoria = SelecaoMultipla.normalizar(idCategoria);
        escopoTitular = SelecaoMultipla.normalizar(escopoTitular);
        idTitular = SelecaoMultipla.normalizar(idTitular);
        situacaoConferencia = SelecaoMultipla.normalizar(situacaoConferencia);
        statusValidade = SelecaoMultipla.normalizar(statusValidade);
    }
}
