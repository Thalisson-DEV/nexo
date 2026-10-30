package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.GranularidadeDashboard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dashboard executivo do financeiro: solicitações de compra e de pagamento numa
 * tela só, mais a conformidade documental que vem do repositório.
 *
 * <p>Organiza-se em quatro leituras que respondem perguntas diferentes:</p>
 * <ol>
 *   <li><b>Quanto</b> — {@code resumo} e {@code valores}: volume, dinheiro em cada
 *       estágio e o que se perde entre eles;</li>
 *   <li><b>Como flui</b> — {@code funil}, {@code serieTemporal}, {@code ciclo},
 *       {@code envelhecimento} e {@code vencimentos}: onde o processo trava, há
 *       quanto tempo e o que vence a seguir;</li>
 *   <li><b>Onde dói</b> — os rankings por base, centro de custo, obra, fornecedor,
 *       tipo de despesa, formulário e solicitante;</li>
 *   <li><b>O que fazer</b> — {@code prioridades}, {@code qualidade} e o bloco
 *       {@code conformidade}.</li>
 * </ol>
 *
 * <p>Compras e pagamentos são somados por padrão, e o filtro {@code tipo} restringe
 * a um só domínio. Somá-los é a leitura correta para quem olha a despesa da
 * empresa: os dois consomem o mesmo orçamento e disputam a mesma fila de
 * aprovação. Onde a distinção importa — funil, envelhecimento, valor autorizado —
 * ela aparece quebrada dentro do próprio bloco.</p>
 */
public record DashboardFinanceiroResponse(
        OffsetDateTime inicio,
        OffsetDateTime fim,

        @Schema(description = "Recorte das séries; quando não informado no filtro, "
                + "é escolhido pelo tamanho do período")
        GranularidadeDashboard granularidade,
        OffsetDateTime atualizadoEm,
        PeriodoFinanceiroDashboard periodo,

        // Cartões
        ResumoSolicitacoesDashboard resumo,
        ResumoValoresDashboard valores,
        CicloSolicitacaoDashboard ciclo,
        ConformidadeDocumentalDashboard conformidade,
        QualidadeDadosFinanceiroDashboard qualidade,

        @Schema(description = "Indicadores-chave com a leitura do período anterior ao lado. "
                + "Chaves: valorAprovado, taxaAprovacao, tempoDecisao, backlogEmAberto, coberturaFiscal")
        Map<String, IndicadorComparativoFinanceiro> indicadores,

        // Gráficos de fluxo
        @Schema(description = "Conversão da coorte do período: abertas → submetidas → aprovadas, "
                + "com rejeitadas e canceladas como vazamento")
        List<FunilFinanceiroItem> funil,

        List<DistribuicaoStatusFinanceiroItem> porStatus,

        @Schema(description = "Evolução por DIA, MES ou ANO — todos os baldes, inclusive zerados")
        List<SerieFinanceiroItem> serieTemporal,

        @Schema(description = "Backlog da fila de decisão por idade na etapa atual, considerando "
                + "todo o histórico pendente até o fim do período")
        List<FaixaEnvelhecimentoFinanceiroItem> envelhecimento,

        @Schema(description = "Régua de vencimentos do que já foi aprovado e ainda não tem comprovante")
        List<FaixaVencimentoItem> vencimentos,

        // Rankings
        List<RankingFinanceiroItem> porBase,
        List<RankingFinanceiroItem> porCentroCusto,
        List<RankingFinanceiroItem> porObra,
        List<RankingFinanceiroItem> porFornecedor,
        List<RankingFinanceiroItem> porTipoDespesa,
        List<RankingFinanceiroItem> porFormulario,
        List<RankingFinanceiroItem> porSolicitante,

        // Repositório documental
        @Schema(description = "Documentos recebidos no período, por natureza")
        List<DistribuicaoDocumentoItem> documentosPorTipo,

        @Schema(description = "Documentos recebidos no período, por situação de conferência")
        List<DistribuicaoDocumentoItem> documentosPorSituacao,

        // Ação
        List<PrioridadeFinanceiraItem> prioridades
) {
}
