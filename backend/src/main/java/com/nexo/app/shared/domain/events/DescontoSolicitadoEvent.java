package com.nexo.app.shared.domain.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Um desconto acabou de ser registrado e ainda não foi validado.
 *
 * <p>O módulo de descontos publica e segue adiante: quem decide se alguém será
 * avisado, quem será e com que texto são as regras configuradas para o gatilho
 * {@code desconto.solicitado}. Se não houver nenhuma, o evento simplesmente não
 * produz efeito.</p>
 *
 * <p>As chaves do {@link #contexto()} correspondem às variáveis declaradas pelo
 * gatilho no catálogo — é o que permite ao administrador escrever
 * {@code "{{colaborador}} teve um desconto de {{motivo}}"} e ter certeza de que os
 * valores existirão.</p>
 */
public record DescontoSolicitadoEvent(
        UUID idDesconto,
        UUID idSolicitante,
        String motivo,
        Integer parcelas,
        String dataOcorrencia,
        UUID idColaborador
) implements EventoNotificavel {

    /** Sem o colaborador: mantém quem ainda publica pelo formato antigo. */
    public DescontoSolicitadoEvent(UUID idDesconto, UUID idSolicitante, String motivo, Integer parcelas, String dataOcorrencia) {
        this(idDesconto, idSolicitante, motivo, parcelas, dataOcorrencia, null);
    }

    public static final String CHAVE_GATILHO = "desconto.solicitado";

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
        // HashMap em vez de Map.of: valores nulos são legítimos aqui (um desconto pode
        // não ter data de ocorrência informada) e Map.of não os aceita.
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("motivo", motivo);
        contexto.put("parcelas", parcelas);
        contexto.put("dataOcorrencia", dataOcorrencia);
        contexto.put("idDesconto", idDesconto);
        // O colaborador descontado: as automações chegam por ele à base, à função e ao usuário.
        contexto.put("idColaborador", idColaborador);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        return idSolicitante == null ? Map.of() : Map.of("solicitante", Set.of(idSolicitante));
    }
}
