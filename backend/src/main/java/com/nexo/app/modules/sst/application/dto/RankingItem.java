package com.nexo.app.modules.sst.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item genérico de ranking para os dashboards (inspetores, processos, equipes,
 * formulários, colaboradores, perguntas...). {@code percentual} é a participação
 * do item no total considerado.
 */
public record RankingItem(
        UUID id,
        String nome,
        long total,
        BigDecimal percentual
) {}
