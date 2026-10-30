package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.ApresentacaoComunicado;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.domain.StatusComunicado;
import com.nexo.app.modules.notificacao.domain.TipoComunicado;

/**
 * Visão de administração do comunicado — o que a tela de gestão do mural exibe.
 */
public record ComunicadoResponse(
        UUID id,
        String titulo,
        String resumo,
        String conteudo,
        TipoComunicado tipo,
        String tipoRotulo,
        ApresentacaoComunicado apresentacao,
        String apresentacaoRotulo,
        StatusComunicado status,
        String statusRotulo,
        String videoUrl,
        String acaoRotulo,
        String acaoDeeplink,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim,
        OffsetDateTime publicadoEm,
        boolean fixado,
        boolean exigeConfirmacao,
        boolean notificarNoSininho,
        boolean notificarPorEmail,
        PrioridadeNotificacao prioridadeNotificacao,
        /** Publicado com início no futuro — a interface mostra como "agendado". */
        boolean agendado,
        boolean vigente,
        List<SegmentoComunicadoResponse> segmentos,
        List<AnexoComunicadoResponse> anexos,
        OffsetDateTime dataCriacao
) {
}
