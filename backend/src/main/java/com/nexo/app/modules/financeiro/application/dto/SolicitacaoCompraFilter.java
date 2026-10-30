package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.GrauPrioridade;
import com.nexo.app.modules.financeiro.domain.FormaPagamento;
import com.nexo.app.modules.financeiro.domain.LocalAquisicao;
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

/** Filtros da listagem de solicitações de compra. */
public record SolicitacaoCompraFilter(

        @Schema(description = "Busca rápida por textos, fornecedor, dados bancários protegidos e códigos dos anexos")
        String buscaGlobal,

        @Schema(description = "Número sequencial exato", example = "1042")
        Long numero,

        StatusSolicitacao status,

        @Schema(description = "Variante de formulário utilizada")
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

        @Schema(description = "Usuário que abriu a solicitação")
        UUID usuarioSolicitanteId,

        @Schema(description = "Traz apenas as solicitações do usuário autenticado")
        Boolean apenasMinhas,

        @Schema(description = "Valor mínimo solicitado")
        BigDecimal valorMinimo,

        @Schema(description = "Valor máximo solicitado")
        BigDecimal valorMaximo,

        @Schema(description = "Quantidade mínima")
        BigDecimal quantidadeMinima,

        @Schema(description = "Quantidade máxima")
        BigDecimal quantidadeMaxima,

        @Schema(description = "Número mínimo de parcelas")
        Integer parcelasMinimas,

        @Schema(description = "Número máximo de parcelas")
        Integer parcelasMaximas,

        LocalDate vencimentoInicio,

        LocalDate vencimentoFim,

        Boolean temNf,

        @Schema(description = "Busca parcial pelo número da nota fiscal")
        String numeroNf,

        @Schema(description = "Valor mínimo autorizado")
        BigDecimal valorAprovadoMinimo,

        @Schema(description = "Valor máximo autorizado")
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
