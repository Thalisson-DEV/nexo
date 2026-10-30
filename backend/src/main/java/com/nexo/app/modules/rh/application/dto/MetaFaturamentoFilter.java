package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/** Filtro dinâmico da listagem de metas de faturamento. */
public record MetaFaturamentoFilter(
        UUID idEquipe,
        UUID idBaseOperacional,
        UUID idProcesso,
        PeriodicidadeMetaFaturamento periodicidade,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Retorna apenas as metas cuja vigência cobre esta data")
        LocalDate vigenteEm,

        Boolean ativo,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por descrição")
        String buscaGlobal
) {
}
