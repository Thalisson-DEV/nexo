package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O termo de desconto foi recusado.
 *
 * <p>Diferente dos demais desfechos, este não passa o bastão adiante: ele volta.
 * Quem registrou o desconto é a única pessoa que pode corrigir o que motivou a
 * recusa, e sem este aviso ela descobriria a rejeição apenas se voltasse à tela por
 * conta própria.</p>
 *
 * <p>O {@code motivoRejeicao} viaja no contexto porque a notificação sem ele obrigaria
 * um clique para saber o que houve — e o texto da recusa é curto justamente para caber
 * no aviso.</p>
 */
public record DescontoRejeitadoEvent(
        UUID idDesconto,
        UUID idSolicitante,
        UUID idRejeitador,
        String motivo,
        String motivoRejeicao,
        UUID idColaborador
) implements EventoNotificavel {

    /** Sem o colaborador: mantém quem ainda publica pelo formato antigo. */
    public DescontoRejeitadoEvent(UUID idDesconto, UUID idSolicitante, UUID idRejeitador, String motivo, String motivoRejeicao) {
        this(idDesconto, idSolicitante, idRejeitador, motivo, motivoRejeicao, null);
    }

    public static final String CHAVE_GATILHO = "desconto.rejeitado";

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idDesconto;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("motivo", motivo);
        contexto.put("motivoRejeicao", motivoRejeicao);
        contexto.put("idDesconto", idDesconto);
        // O colaborador descontado: as automações chegam por ele à base, à função e ao usuário.
        contexto.put("idColaborador", idColaborador);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idSolicitante == null || idSolicitante.equals(idRejeitador)) {
            return Map.of();
        }
        return Map.of("solicitante", Set.of(idSolicitante));
    }
}
