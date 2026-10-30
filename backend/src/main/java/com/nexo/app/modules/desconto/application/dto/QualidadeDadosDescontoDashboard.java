package com.nexo.app.modules.desconto.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * O quanto os números da tela merecem confiança.
 *
 * <p>Um termo sem anexo e sem descrição não é só um cadastro incompleto: é um desconto
 * que a validação vai ter que devolver, e que hoje aparece somando valor como se
 * estivesse pronto. Medir isso ao lado do resto evita a leitura otimista de um backlog
 * que na verdade está travado por falta de comprovação.</p>
 */
public record QualidadeDadosDescontoDashboard(
        @Schema(description = "Alteração mais recente entre os registros do período")
        OffsetDateTime ultimaCargaEm,

        @Schema(description = "Pendentes sem nenhum anexo — sem comprovação para validar")
        long pendentesSemAnexo,

        @Schema(description = "Pendentes sem nenhum item ativo — não é possível validar o valor")
        long pendentesSemItens,

        @Schema(description = "Pendentes sem observação preenchida")
        long pendentesSemObservacao,

        @Schema(description = "Percentual de campos de apoio preenchidos entre os pendentes")
        BigDecimal coberturaDocumentalPercentual,

        @Schema(description = "Termos cujo colaborador não foi encontrado no cadastro de RH")
        long colaboradoresNaoIdentificados
) {
}
