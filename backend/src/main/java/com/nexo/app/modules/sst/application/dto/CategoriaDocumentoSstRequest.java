package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ContextoCategoriaDocumentoSst;
import com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst;
import com.nexo.app.modules.sst.domain.NaturezaCategoriaDocumentoSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record CategoriaDocumentoSstRequest(
        @NotBlank @Size(max = 80) String codigo,
        @NotBlank @Size(max = 160) String nome,
        @Size(max = 500) String descricao,
        @NotNull ContextoCategoriaDocumentoSst contexto,
        @NotNull NaturezaCategoriaDocumentoSst natureza,
        @NotNull EscopoTitularDocumentoSst escopoTitular,
        @Size(max = 60) String jurisdicao,
        @Size(max = 160) String autoridadeEmissora,
        @Size(max = 160) String identificadorFonte,
        @Size(max = 80) String edicaoFonte,
        @Size(max = 500) String urlFonte,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        boolean confirmada,
        boolean exigeNumero,
        boolean exigeEmissor,
        boolean exigeDataEmissao,
        boolean exigeDataValidade,
        boolean semValidade,
        boolean obrigatoria,
        @PositiveOrZero int retencaoMinimaAnos,
        @Size(max = 160) String referenciaRetencao,
        Set<Integer> faixasAlertaDias
) {
}
