package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst;
import com.nexo.app.modules.sst.domain.ModalidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentoSstMetadataRequest(
        @NotNull UUID idCategoria,
        @NotNull UUID idBase,
        @NotNull TipoCorpoGovernancaSst contexto,
        @NotNull EscopoTitularDocumentoSst escopoTitular,
        @NotNull UUID idTitular,
        UUID idColaborador,
        UUID idTreinamento,
        @Size(max = 120) String numeroDocumento,
        @Size(max = 200) String emissor,
        @Size(max = 2) String uf,
        @Size(max = 300) String referenciaNormativa,
        LocalDate dataEmissao,
        LocalDate dataValidade,
        LocalDate dataConclusaoTreinamento,
        @Positive Integer cargaHorariaMinutos,
        ModalidadeTreinamentoSst modalidade,
        @Size(max = 4000) String observacoes
) {
}
