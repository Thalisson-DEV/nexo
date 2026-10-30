package com.nexo.app.modules.sst.domain;

import java.time.LocalDate;
import java.util.UUID;

public record DadosDocumentoSst(
        UUID idBase,
        TipoCorpoGovernancaSst contexto,
        EscopoTitularDocumentoSst escopoTitular,
        UUID idTitular,
        UUID idColaborador,
        UUID idTreinamento,
        String numeroDocumento,
        String emissor,
        String uf,
        String referenciaNormativa,
        LocalDate dataEmissao,
        LocalDate dataValidade,
        LocalDate dataConclusaoTreinamento,
        Integer cargaHorariaMinutos,
        ModalidadeTreinamentoSst modalidade,
        String observacoes
) {
}
