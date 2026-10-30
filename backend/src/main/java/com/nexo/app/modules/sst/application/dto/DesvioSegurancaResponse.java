package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusDesvio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DesvioSegurancaResponse(
        UUID id,
        UUID idPergunta,
        UUID idOpcaoResposta,
        GravidadeDesvio gravidade,
        String descricao,
        String acaoCorretiva,
        LocalDate prazo,
        StatusDesvio status,
        OffsetDateTime dataResolucao,
        List<ColaboradorResponse> colaboradores,
        List<AnexoDesvioResponse> anexos
) {}
