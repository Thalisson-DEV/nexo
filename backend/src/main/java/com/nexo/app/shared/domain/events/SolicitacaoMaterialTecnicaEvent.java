package com.nexo.app.shared.domain.events;

import java.util.UUID;

/**
 * Evento de integracao do workflow tecnico. Os valores sao snapshots e nao
 * exigem que consumidores consultem entidades internas do modulo de estoque.
 */
public record SolicitacaoMaterialTecnicaEvent(
        Tipo tipo,
        UUID idSolicitacao,
        String tipoTecnico,
        String tipoObra,
        String ocorrencia,
        UUID idObra,
        UUID idEquipe,
        UUID idAlmoxarifadoAtendimento,
        String statusSeparacao,
        String numeroReserva,
        UUID idOperacao,
        UUID idOperacaoOriginal
) {
    public enum Tipo {
        OCORRENCIA_CORRIGIDA,
        RESERVA_REGISTRADA,
        RESERVA_ALTERADA,
        ADICAO_PRONTA,
        ADICAO_SEPARADA,
        ADICAO_ENTREGUE,
        DEVOLUCAO_PRONTA,
        DEVOLUCAO_RECEBIDA,
        ESTORNO_TECNICO
    }
}
