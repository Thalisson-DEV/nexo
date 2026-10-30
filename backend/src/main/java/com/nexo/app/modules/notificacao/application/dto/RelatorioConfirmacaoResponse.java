package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Prova de comunicação de um comunicado que exige "Li e entendi".
 *
 * <p>É o produto final do {@code exigeConfirmacao}: sem o denominador (quantos
 * deveriam ler) e a lista de quem não leu, a confirmação não serviria de evidência
 * para SST ou auditoria.</p>
 */
public record RelatorioConfirmacaoResponse(
        UUID idComunicado,
        String titulo,
        boolean exigeConfirmacao,
        int totalDestinatarios,
        int totalConfirmados,
        int totalVisualizados,
        List<ConfirmacaoUsuario> confirmados,
        List<PendenteConfirmacao> pendentes
) {

    public record ConfirmacaoUsuario(
            UUID idUsuario,
            String login,
            OffsetDateTime visualizadoEm,
            OffsetDateTime confirmadoEm
    ) {
    }

    public record PendenteConfirmacao(
            UUID idUsuario,
            String login,
            /** Abriu mas não confirmou — diferente de quem nunca viu. */
            OffsetDateTime visualizadoEm
    ) {
    }
}
