package com.nexo.app.modules.frota.application.dto;

import com.nexo.app.modules.frota.domain.OrigemLeituraOdometro;
import com.nexo.app.modules.frota.domain.SituacaoLeituraOdometro;
import com.nexo.app.shared.application.SelecaoMultipla;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LeituraOdometroFilter(
        UUID idVeiculo,
        List<UUID> idsBase,
        List<SituacaoLeituraOdometro> situacoes,
        List<OrigemLeituraOdometro> origens,
        OffsetDateTime de,
        OffsetDateTime ate
) {
    public LeituraOdometroFilter {
        idsBase = SelecaoMultipla.normalizar(idsBase);
        situacoes = SelecaoMultipla.normalizar(situacoes);
        origens = SelecaoMultipla.normalizar(origens);
    }
}
