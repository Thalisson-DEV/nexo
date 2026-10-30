package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.ApresentacaoComunicado;
import com.nexo.app.modules.notificacao.domain.TipoComunicado;

/**
 * O comunicado como o destinatário o vê.
 *
 * <p>Difere da visão de administração em duas coisas: não expõe o público-alvo (não
 * é da conta de quem lê) e traz o estado <em>desta</em> pessoa — se já viu, se
 * precisa confirmar — que é o que decide como o front apresenta.</p>
 */
public record ComunicadoMuralResponse(
        UUID id,
        String titulo,
        String resumo,
        String conteudo,
        TipoComunicado tipo,
        String tipoRotulo,
        ApresentacaoComunicado apresentacao,
        String videoUrl,
        String acaoRotulo,
        String acaoDeeplink,
        boolean fixado,
        boolean exigeConfirmacao,
        boolean permiteAdiar,
        boolean visualizado,
        boolean confirmado,
        OffsetDateTime dataInicio,
        OffsetDateTime dataFim,
        List<AnexoComunicadoResponse> anexos
) {
}
