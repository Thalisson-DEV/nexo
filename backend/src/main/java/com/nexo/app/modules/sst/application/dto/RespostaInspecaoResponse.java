package com.nexo.app.modules.sst.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RespostaInspecaoResponse(
        UUID id,
        UUID idPergunta,
        List<UUID> opcoesSelecionadas,
        String valorTexto,
        BigDecimal valorNumero,
        LocalDate valorData
) {}
