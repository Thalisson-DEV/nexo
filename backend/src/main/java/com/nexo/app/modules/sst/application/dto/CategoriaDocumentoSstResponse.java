package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ContextoCategoriaDocumentoSst;
import com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst;
import com.nexo.app.modules.sst.domain.NaturezaCategoriaDocumentoSst;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record CategoriaDocumentoSstResponse(
        UUID id,
        String codigo,
        int revisaoCategoria,
        String nome,
        String descricao,
        ContextoCategoriaDocumentoSst contexto,
        NaturezaCategoriaDocumentoSst natureza,
        EscopoTitularDocumentoSst escopoTitular,
        String jurisdicao,
        String autoridadeEmissora,
        String identificadorFonte,
        String edicaoFonte,
        String urlFonte,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        boolean confirmada,
        OffsetDateTime dataConfirmacao,
        boolean exigeNumero,
        boolean exigeEmissor,
        boolean exigeDataEmissao,
        boolean exigeDataValidade,
        boolean semValidade,
        boolean obrigatoria,
        int retencaoMinimaAnos,
        String referenciaRetencao,
        Set<Integer> faixasAlertaDias,
        boolean ativa,
        Long versao
) {
}
