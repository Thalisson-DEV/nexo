package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dashboard executivo de acidentalidade.
 *
 * <p>Os indicadores do período são calculados sobre os eventos <b>ocorridos</b> (não os
 * registrados), e os cancelados ficam de fora. Métricas explicitamente históricas,
 * como dias sem eventos, ignoram a janela informada.</p>
 */
@Schema(description = "Indicadores de acidentalidade do período")
public record DashboardEventosSstResponse(

        OffsetDateTime inicio,
        OffsetDateTime fim,
        GranularidadeDashboard granularidade,
        OffsetDateTime geradoEm,

        ResumoEventosDashboard resumo,

        @Schema(description = "Série temporal no recorte escolhido, sem buracos: baldes vazios vêm zerados")
        List<SerieEventosItem> serie,

        @Schema(description = "Pirâmide de Bird: base de desvios apontados em inspeção contra o topo com lesão")
        List<PiramideAcidentalidadeItem> piramide,

        @Schema(description = "Dias sem evento com lesão por base — ignora o período, olha a série inteira")
        List<DiasSemAcidenteItem> diasSemAcidente,

        @Schema(description = "Eventos abertos por idade da ocorrência")
        List<FaixaBacklogEventos> backlog,

        List<RankingItem> porClassificacao,
        List<RankingItem> porAtividade,
        List<RankingItem> porGestor,
        List<RankingItem> porBaseOperacional,
        List<RankingItem> porEquipe,
        List<RankingItem> porProcesso,

        @Schema(description = "Ranking por local da lesão, restrito aos eventos que implicam lesão")
        List<RankingItem> porLocalLesao
) {
}
