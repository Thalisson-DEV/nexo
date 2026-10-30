package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.PeriodicidadeMeta;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Meta de inspeções cadastrada. {@code janelaAtualInicio}/{@code janelaAtualFim}
 * mostram a janela de apuração vigente hoje — nulos quando a meta não está vigente.
 */
public record MetaInspecaoResponse(
        UUID id,
        UUID idTipoInspecao,
        String nomeTipoInspecao,
        EscopoMeta escopo,
        UUID idFuncao,
        String nomeFuncao,
        UUID idColaborador,
        String nomeColaborador,
        int quantidade,
        PeriodicidadeMeta periodicidade,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        String descricao,
        boolean ativo,
        LocalDate janelaAtualInicio,
        LocalDate janelaAtualFim
) {
}
