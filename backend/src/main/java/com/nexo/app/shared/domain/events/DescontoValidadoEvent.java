package com.nexo.app.shared.domain.events;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O termo de desconto foi validado e passou a aguardar a baixa.
 *
 * <p>É o ponto de passagem de bastão do fluxo: a partir daqui o desconto não depende
 * mais de quem o solicitou nem de quem o validou, e sim de quem faz a baixa. Por isso
 * o evento existe mesmo que ninguém precise "confirmar" nada — o destinatário natural
 * é o próximo responsável, endereçado por permissão na regra, e não por papel: quem
 * fará a baixa não está escrito em lugar nenhum deste registro.</p>
 *
 * <p>O papel {@code solicitante} continua disponível para a regra que quiser devolver
 * a confirmação a quem pediu.</p>
 */
public record DescontoValidadoEvent(
        UUID idDesconto,
        UUID idSolicitante,
        UUID idValidador,
        String motivo,
        Integer parcelas,
        BigDecimal valorValidado,
        UUID idColaborador
) implements EventoNotificavel {

    /** Sem o colaborador: mantém quem ainda publica pelo formato antigo. */
    public DescontoValidadoEvent(UUID idDesconto, UUID idSolicitante, UUID idValidador, String motivo, Integer parcelas, BigDecimal valorValidado) {
        this(idDesconto, idSolicitante, idValidador, motivo, parcelas, valorValidado, null);
    }

    public static final String CHAVE_GATILHO = "desconto.validado";

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
        contexto.put("valorValidado", valorValidado);
        contexto.put("idDesconto", idDesconto);
        // O colaborador descontado: as automações chegam por ele à base, à função e ao usuário.
        contexto.put("idColaborador", idColaborador);
        return contexto;
    }

    /**
     * O validador não ocupa papel algum: notificar quem acabou de agir é ruído. Se ele
     * for também o solicitante, o papel fica vazio e a regra de confirmação
     * simplesmente não alcança ninguém desta vez.
     */
    @Override
    public Map<String, Set<UUID>> papeis() {
        if (idSolicitante == null || idSolicitante.equals(idValidador)) {
            return Map.of();
        }
        return Map.of("solicitante", Set.of(idSolicitante));
    }
}
