package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoMeta;
import com.nexo.app.modules.sst.domain.PeriodicidadeMeta;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtro dinâmico para listagem de metas de inspeção.
 */
public record MetaInspecaoFilter(
        UUID idTipoInspecao,
        EscopoMeta escopo,
        UUID idFuncao,
        UUID idColaborador,
        PeriodicidadeMeta periodicidade,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Retorna apenas as metas cuja vigência cobre esta data")
        LocalDate vigenteEm,

        Boolean ativo,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por descrição")
        String buscaGlobal
) {
}
