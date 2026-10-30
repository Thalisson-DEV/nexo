package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Dashboard de INSPEÇÕES de segurança, organizado para leitura direta e para gráficos:
 * dois blocos de cartões (números do período e cumprimento das metas) e quatro conjuntos
 * de séries — evolução no tempo, comparativo por inspetor e três distribuições simples.
 *
 * <p>Toda métrica sai dos dados já existentes: inspeções lançadas, desvios registrados e
 * metas cadastradas. Nenhum campo depende de configuração adicional.</p>
 */
public record DashboardInspecoesResponse(
        OffsetDateTime inicio,
        OffsetDateTime fim,

        @Schema(description = "Recorte das séries; quando não informado no filtro, é escolhido pelo tamanho do período")
        GranularidadeDashboard granularidade,
        OffsetDateTime atualizadoEm,

        // Cartões
        ResumoInspecoesDashboard resumo,
        ResumoMetaDashboard meta,

        @Schema(description = "Cobertura do efetivo pelas inspeções do período")
        ResumoColaboradoresDashboard colaboradores,

        // Gráficos
        @Schema(description = "Evolução no tempo: realizado x meta por dia, mês ou ano")
        List<SerieMetaItem> serieTemporal,

        @Schema(description = "Comparativo realizado x meta por inspetor, do maior para o menor volume")
        List<InspetorMetaItem> porInspetor,

        @Schema(description = "Equipes inspecionadas: volume, conformidade e tempo desde a última inspeção")
        List<EquipeInspecaoItem> porEquipe,

        @Schema(description = "Colaboradores inspecionados: frequência, desvios recebidos e última inspeção")
        List<ColaboradorInspecionadoItem> porColaborador,

        List<RankingItem> porProcesso,
        List<RankingItem> porProcessoInspetor,
        List<RankingItem> porBaseOperacional,
        List<RankingItem> porFormulario,
        List<RankingItem> porTipoInspecao
) {
}
