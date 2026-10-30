package com.nexo.app.modules.desconto.application.dto;

import com.nexo.app.modules.desconto.domain.GranularidadeDashboard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dashboard executivo de descontos em folha.
 *
 * <p>Organiza-se em quatro leituras que respondem perguntas diferentes:</p>
 * <ol>
 *   <li><b>Quanto</b> — {@code resumo} e {@code financeiro}: volume, dinheiro em cada
 *       etapa e o que se perde entre elas;</li>
 *   <li><b>Como flui</b> — {@code funil}, {@code serieTemporal}, {@code ciclo} e
 *       {@code envelhecimento}: onde o processo trava e há quanto tempo;</li>
 *   <li><b>Onde dói</b> — os rankings por motivo, tipo de equipamento, colaborador,
 *       base, centro de custo, função e solicitante;</li>
 *   <li><b>O que fazer</b> — {@code prioridades} e {@code qualidade}.</li>
 * </ol>
 *
 * <p>Toda métrica sai de dados já existentes: termos lançados, itens, anexos e as
 * datas de cada transição. Nenhum campo depende de configuração adicional.</p>
 */
public record DashboardDescontosResponse(
        OffsetDateTime inicio,
        OffsetDateTime fim,

        @Schema(description = "Recorte das séries; quando não informado no filtro, "
                + "é escolhido pelo tamanho do período")
        GranularidadeDashboard granularidade,
        OffsetDateTime atualizadoEm,
        PeriodoDescontoDashboard periodo,

        // Cartões
        ResumoDescontosDashboard resumo,
        ResumoFinanceiroDashboard financeiro,
        CicloDescontoDashboard ciclo,
        QualidadeDadosDescontoDashboard qualidade,

        @Schema(description = "Indicadores-chave com a leitura do período anterior ao lado. "
                + "Chaves: valorBaixado, taxaAprovacao, valorEmAberto, tempoValidacao")
        Map<String, IndicadorComparativoDesconto> indicadores,

        // Gráficos de fluxo
        @Schema(description = "Conversão da coorte do período: solicitados → validados → baixados")
        List<FunilEtapaItem> funil,

        List<DistribuicaoStatusItem> porStatus,

        @Schema(description = "Evolução por DIA, MES ou ANO — todos os baldes, inclusive zerados (Data de Ocorrência)")
        List<SerieDescontoItem> serieTemporal,

        @Schema(description = "Evolução por DIA, MES ou ANO — todos os baldes, inclusive zerados (Data de Criação)")
        List<SerieDescontoItem> serieTemporalCriacao,

        @Schema(description = "Backlog por idade na etapa atual, considerando todo o histórico "
                + "pendente até o fim do período")
        List<FaixaEnvelhecimentoItem> envelhecimento,

        // Rankings
        List<RankingDescontoItem> porMotivo,
        List<TipoEquipamentoDescontoItem> porTipoEquipamento,
        List<ColaboradorDescontoItem> porColaborador,
        List<RankingDescontoItem> porBaseOperacional,
        List<RankingDescontoItem> porCentroCusto,
        List<RankingDescontoItem> porFuncao,
        List<RankingDescontoItem> porSolicitante,

        // Ação
        List<PrioridadeDescontoItem> prioridades
) {
}
