package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoTitularDocumentoSst;
import com.nexo.app.modules.sst.domain.ModalidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.SituacaoConferenciaDocumentoSst;
import com.nexo.app.modules.sst.domain.StatusValidadeDocumentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentoSstResponse(
        UUID id,
        UUID idCategoria,
        String categoriaCodigo,
        String categoriaNome,
        int categoriaRevisao,
        UUID idBase,
        TipoCorpoGovernancaSst contexto,
        EscopoTitularDocumentoSst escopoTitular,
        UUID idTitular,
        UUID idColaborador,
        UUID idTreinamento,
        String nomeArquivo,
        String contentType,
        long tamanhoBytes,
        String sha256,
        String numeroDocumento,
        String emissor,
        String uf,
        String referenciaNormativa,
        LocalDate dataEmissao,
        LocalDate dataValidade,
        LocalDate dataConclusaoTreinamento,
        Integer cargaHorariaMinutos,
        ModalidadeTreinamentoSst modalidade,
        SituacaoConferenciaDocumentoSst situacaoConferencia,
        String motivoRejeicao,
        StatusValidadeDocumentoSst statusValidade,
        UUID idDocumentoSubstituido,
        UUID idDocumentoSubstituto,
        boolean atual,
        boolean arquivado,
        LocalDate dataRetencaoAte,
        String referenciaRetencao,
        OffsetDateTime dataCriacao,
        Long versao
) {
}
