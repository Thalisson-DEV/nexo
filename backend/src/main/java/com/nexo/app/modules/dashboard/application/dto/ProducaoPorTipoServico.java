package com.nexo.app.modules.dashboard.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Notas e produção de um tipo de serviço dentro de um recorte. As notas sem tipo
 * (lançadas antes do cadastro ou por versões antigas do app) vêm agrupadas com
 * {@code idTipoServico} nulo e descrição "Não informado".
 */
public record ProducaoPorTipoServico(
        UUID idTipoServico,
        String codigo,
        String descricao,
        long notas,
        BigDecimal producao,
        BigDecimal valorMedioNota,
        /* Fatia das notas do recorte, em percentual com uma casa. */
        BigDecimal percentualNotas
) {
}
