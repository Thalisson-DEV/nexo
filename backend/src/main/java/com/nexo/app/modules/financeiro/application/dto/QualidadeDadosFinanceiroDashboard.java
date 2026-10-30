package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * O quanto os números da tela merecem confiança.
 *
 * <p>Uma solicitação sem fornecedor identificado e sem descrição não é só um
 * cadastro incompleto: é uma despesa que ninguém consegue reconciliar depois, e
 * que hoje aparece somando valor como se estivesse pronta. Medir isso ao lado do
 * resto evita a leitura otimista de um total que, olhado de perto, não se
 * sustenta.</p>
 *
 * <p>{@code fornecedoresNaoHomologados} conta os usados no período que ainda não
 * passaram pela curadoria do financeiro. Não é irregularidade — o solicitante pode
 * cadastrar um fornecedor no meio do preenchimento, e é assim de propósito, para
 * não travar a operação. É dívida de conferência, e ela se acumula em silêncio se
 * ninguém a mede.</p>
 */
public record QualidadeDadosFinanceiroDashboard(
        @Schema(description = "Alteração mais recente entre as solicitações do período")
        OffsetDateTime ultimaMovimentacaoEm,

        @Schema(description = "Sem fornecedor cadastrado nem nome livre preenchido")
        long semFornecedorIdentificado,

        @Schema(description = "Sem descrição do que está sendo pedido")
        long semDescricao,

        @Schema(description = "Sem centro de custo — a despesa não tem a quem ser rateada")
        long semCentroCusto,

        @Schema(description = "Sem base operacional informada")
        long semBase,

        @Schema(description = "Fornecedores distintos utilizados no período que ainda não foram homologados")
        long fornecedoresNaoHomologados,

        @Schema(description = "Solicitações do período que usam fornecedor não homologado")
        long solicitacoesComFornecedorNaoHomologado,

        @Schema(description = "Aprovadas no período sem nenhum documento no repositório")
        long aprovadasSemNenhumDocumento,

        @Schema(description = "Percentual dos campos de apoio preenchidos nas solicitações do período")
        BigDecimal percentualPreenchimento
) {
}
