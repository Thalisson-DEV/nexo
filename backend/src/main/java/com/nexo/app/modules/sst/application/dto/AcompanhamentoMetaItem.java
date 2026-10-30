package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.PeriodicidadeMeta;
import com.nexo.app.modules.sst.domain.StatusMeta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Situação de um colaborador diante da meta que se aplica a ele na janela apurada.
 *
 * @param origem         de onde veio a meta: da FUNCAO do colaborador ou uma meta individual
 * @param possuiUsuario  se ele tem usuário vinculado; quando {@code false} o realizado é
 *                       zero por falta de acesso — ele não consegue lançar inspeção —, e
 *                       não por falta de trabalho
 * @param realizado      inspeções lançadas por ele (como inspetor) dentro da janela
 * @param saldo          quantas faltam para atingir a meta (0 quando já atingida)
 * @param idCentroCusto  centro de custo da base operacional do colaborador; nulo quando
 *                       ele não tem base vinculada
 */
public record AcompanhamentoMetaItem(
        UUID idMeta,
        UUID idTipoInspecao,
        String nomeTipoInspecao,
        EscopoMeta origem,
        UUID idColaborador,
        String nomeColaborador,
        boolean possuiUsuario,
        UUID idFuncao,
        String nomeFuncao,
        UUID idBaseOperacional,
        String nomeBaseOperacional,
        UUID idCentroCusto,
        String nomeCentroCusto,
        UUID idProcesso,
        String nomeProcesso,
        PeriodicidadeMeta periodicidade,
        LocalDate janelaInicio,
        LocalDate janelaFim,
        long diasRestantes,
        int meta,
        long realizado,
        long saldo,
        BigDecimal percentualAtingimento,
        StatusMeta status
) {
}
