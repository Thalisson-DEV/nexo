package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusDesvio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Item autocontido para a caixa de tratamento rápido do usuário.
 */
public record MeuDesvioResponse(
        UUID id,
        UUID idInspecao,
        OffsetDateTime dataInspecao,
        ProcessoNegocioResponse processo,
        ProcessoNegocioResponse processoInspetor,
        BaseOperacionalResponse baseOperacional,
        EquipeResponse equipe,
        UUID idPergunta,
        String pergunta,
        GravidadeDesvio gravidade,
        String descricao,
        String acaoCorretiva,
        LocalDate prazo,
        StatusDesvio status,
        OffsetDateTime dataResolucao,
        boolean vencido,
        List<AnexoDesvioResponse> anexos
) {
}
