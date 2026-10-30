package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Meta da janela vigente da equipe (ciclo comercial de 26 a 25, ou mês civil) e o
 * faturamento que o servidor já recebeu nela, para a equipe acompanhar a própria meta
 * no aplicativo de campo. {@code competencia} vem no formato {@code AAAA-MM}.
 *
 * <p>{@code realizado} soma os lançamentos concluídos da janela até {@code apuradoEm};
 * o aplicativo acrescenta o que ainda não foi enviado.</p>
 */
public record MetaDoCicloResponse(
        PeriodicidadeMetaFaturamento periodicidade,
        String competencia,
        LocalDate inicio,
        LocalDate fim,
        BigDecimal valor,
        BigDecimal metaDiaria,
        int diasUteis,
        BigDecimal realizado,
        OffsetDateTime apuradoEm
) {
}
