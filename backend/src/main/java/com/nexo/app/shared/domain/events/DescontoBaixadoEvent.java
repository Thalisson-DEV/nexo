package com.nexo.app.shared.domain.events;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A baixa do desconto foi registrada e o ciclo se encerrou.
 *
 * <p>Não há próximo responsável a avisar — este é o fim da linha. O evento existe para
 * fechar o laço com quem abriu o termo, que de outro modo ficaria sem saber se o
 * desconto chegou a ser efetivado ou apenas parou de aparecer na sua lista.</p>
 */
public record DescontoBaixadoEvent(
        UUID idDesconto,
        UUID idSolicitante,
        UUID idResponsavelBaixa,
        String motivo,
        Integer parcelas,
        BigDecimal valorBaixado,
        UUID idColaborador
) implements EventoNotificavel {

    /** Sem o colaborador: mantém quem ainda publica pelo formato antigo. */
    public DescontoBaixadoEvent(UUID idDesconto, UUID idSolicitante, UUID idResponsavelBaixa, String motivo, Integer parcelas, BigDecimal valorBaixado) {
        this(idDesconto, idSolicitante, idResponsavelBaixa, motivo, parcelas, valorBaixado, null);
    }

    public static final String CHAVE_GATILHO = "desconto.baixado";

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
        contexto.put("parcelas", parcelas);
        contexto.put("valorBaixado", valorBaixado);
        contexto.put("idDesconto", idDesconto);
        // O colaborador descontado: as automações chegam por ele à base, à função e ao usuário.
        contexto.put("idColaborador", idColaborador);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idSolicitante == null || idSolicitante.equals(idResponsavelBaixa)) {
            return Map.of();
        }
        return Map.of("solicitante", Set.of(idSolicitante));
    }
}
