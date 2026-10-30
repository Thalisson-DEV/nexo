package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.OrigemMembroCipa;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.RepresentacaoCipa;
import com.nexo.app.modules.sst.domain.TitularidadeCipa;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MembroCorpoSstResponse(
        UUID id,
        Long versao,
        UUID idCorpo,
        UUID idColaborador,
        String nomeSnapshot,
        UUID idFuncaoSnapshot,
        String funcaoSnapshot,
        UUID idBaseSnapshot,
        String baseSnapshot,
        String turnoSnapshot,
        PapelMembroGovernancaSst papel,
        RepresentacaoCipa representacao,
        TitularidadeCipa titularidade,
        OrigemMembroCipa origem,
        String cicloEleitoral,
        Integer classificacaoEleitoral,
        String atoDesignacao,
        boolean reeleito,
        LocalDate inicioParticipacao,
        LocalDate fimParticipacao,
        String motivoSaida,
        OffsetDateTime dataRegistroSaida,
        UUID idMembroSubstituido,
        UUID idMembroSubstituto,
        boolean vacanciaPendente,
        boolean conformidadeNormativa,
        String codigoDivergenciaNormativa
) {
}
