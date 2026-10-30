package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.FormaPagamento;
import com.nexo.app.modules.financeiro.domain.GrauPrioridade;
import com.nexo.app.modules.financeiro.domain.LocalAquisicao;
import com.nexo.app.modules.financeiro.domain.MotivoSemCompra;
import com.nexo.app.modules.financeiro.domain.MotivoServicoFrota;
import com.nexo.app.modules.financeiro.domain.NaturezaServicoFrota;
import com.nexo.app.modules.financeiro.domain.RegimeVeiculo;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoDespesa;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Filtros da listagem de solicitações de pagamento. */
public record SolicitacaoPagamentoFilter(

        @Schema(description = "Busca rápida por textos, fornecedor, NF, dados bancários protegidos e códigos dos anexos")
        String buscaGlobal,

        @Schema(description = "Número sequencial exato", example = "507")
        Long numero,

        StatusSolicitacao status,

        UUID formularioId,

        UUID baseId,

        @Schema(description = "Setor / centro de custo (processo de negócio)")
        UUID processoId,

        UUID obraId,

        UUID fornecedorId,

        UUID veiculoId,

        UUID solicitanteColaboradorId,

        GrauPrioridade prioridade,

        LocalAquisicao localAquisicao,

        FormaPagamento formaPagamento,

        RegimeVeiculo regimeVeiculo,

        NaturezaServicoFrota naturezaServico,

        MotivoServicoFrota motivoServico,

        TipoDespesa tipoDespesa,

        @Schema(description = "Pagamentos vinculados a esta compra")
        UUID solicitacaoCompraId,

        @Schema(description = "Filtra por existência de compra prévia")
        Boolean possuiCompraPrevia,

        UUID usuarioSolicitanteId,

        @Schema(description = "Traz apenas as solicitações do usuário autenticado")
        Boolean apenasMinhas,

        BigDecimal valorMinimo,

        BigDecimal valorMaximo,

        BigDecimal quantidadeMinima,

        BigDecimal quantidadeMaxima,

        Integer parcelasMinimas,

        Integer parcelasMaximas,

        @Schema(description = "Vencimento a partir de")
        LocalDate vencimentoInicio,

        @Schema(description = "Vencimento até")
        LocalDate vencimentoFim,

        Boolean temNf,

        String numeroNf,

        MotivoSemCompra motivoSemCompra,

        BigDecimal valorAprovadoMinimo,

        BigDecimal valorAprovadoMaximo,

        UUID usuarioDecisaoId,

        UUID usuarioCancelamentoId,

        OffsetDateTime dataSubmissaoInicio,

        OffsetDateTime dataSubmissaoFim,

        OffsetDateTime dataDecisaoInicio,

        OffsetDateTime dataDecisaoFim,

        OffsetDateTime dataCancelamentoInicio,

        OffsetDateTime dataCancelamentoFim,

        OffsetDateTime dataUltimaAlteracaoInicio,

        OffsetDateTime dataUltimaAlteracaoFim,

        OffsetDateTime dataDesativacaoInicio,

        OffsetDateTime dataDesativacaoFim,

        @Schema(description = "Filtra solicitações com ou sem documentos ativos")
        Boolean possuiDocumentos,

        OffsetDateTime dataCriacaoInicio,

        OffsetDateTime dataCriacaoFim,

        @Schema(description = "Filtra por registro ativo (soft delete)")
        Boolean ativo
) {}
