package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.rh.ComposicaoEquipeApi;

import java.util.List;

/**
 * Dados para a conferência da equipe na abertura do turno: composição atual
 * (veículo, membros e meta) e as opções que o responsável pode escolher,
 * além do catálogo de ações com seus valores para cálculo offline e a meta da
 * janela vigente (ciclo comercial ou mês) com o faturamento já recebido nela.
 */
public record ComposicaoTurnoResponse(
    ComposicaoEquipeApi.ComposicaoEquipe composicao,
    ComposicaoEquipeApi.OpcoesComposicao opcoes,
    boolean usuarioResponsavel,
    List<ContratoApi.AcaoValorItem> acoes,
    MetaDoCicloResponse metaDoCiclo
) {
    public ComposicaoTurnoResponse(ComposicaoEquipeApi.ComposicaoEquipe composicao,
                                   ComposicaoEquipeApi.OpcoesComposicao opcoes,
                                   boolean usuarioResponsavel,
                                   List<ContratoApi.AcaoValorItem> acoes) {
        this(composicao, opcoes, usuarioResponsavel, acoes, null);
    }

    public ComposicaoTurnoResponse(ComposicaoEquipeApi.ComposicaoEquipe composicao,
                                   ComposicaoEquipeApi.OpcoesComposicao opcoes,
                                   boolean usuarioResponsavel) {
        this(composicao, opcoes, usuarioResponsavel, List.of(), null);
    }
}
