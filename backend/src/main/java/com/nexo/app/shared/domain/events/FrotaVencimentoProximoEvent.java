package com.nexo.app.shared.domain.events;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Um documento da frota (veículo ou implemento) ou uma CNH entrou numa faixa de aviso
 * de vencimento.
 *
 * <p>O job diário da frota publica um evento por alvo e faixa; quem recebe e com que
 * texto é decidido pelas regras do gatilho {@code frota.vencimento_proximo}.</p>
 *
 * @param tipoAlvo   {@code DOCUMENTO} ou {@code CNH}
 * @param descricao  o que vence, ex.: "CRLV" ou "CNH"
 * @param referencia de quem é, ex.: a placa, a identificação do implemento ou o nome do condutor
 * @param link       caminho da tela onde o alvo é tratado
 */
public record FrotaVencimentoProximoEvent(
        UUID idAlvo,
        String tipoAlvo,
        String descricao,
        String referencia,
        LocalDate dataVencimento,
        long diasRestantes,
        String link
) implements EventoNotificavel {

    public static final String CHAVE_GATILHO = "frota.vencimento_proximo";

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public String chaveGatilho() {
        return CHAVE_GATILHO;
    }

    @Override
    public UUID idReferencia() {
        return idAlvo;
    }

    @Override
    public Map<String, Object> contexto() {
        Map<String, Object> contexto = new HashMap<>();
        contexto.put("tipoAlvo", tipoAlvo);
        contexto.put("descricao", descricao);
        contexto.put("referencia", referencia);
        contexto.put("dataVencimento", dataVencimento == null ? null : FORMATO.format(dataVencimento));
        contexto.put("diasRestantes", diasRestantes);
        contexto.put("prazo", prazo());
        contexto.put("link", link);
        return contexto;
    }

    @Override
    public Map<String, Set<UUID>> papeis() {
        return Map.of();
    }

    /** Texto do prazo pronto para a frase: "vence hoje", "vence em 7 dias", "venceu há 3 dias". */
    public String prazo() {
        if (diasRestantes == 0) return "vence hoje";
        if (diasRestantes > 0) return "vence em " + diasRestantes + (diasRestantes == 1 ? " dia" : " dias");
        long atraso = -diasRestantes;
        return "venceu há " + atraso + (atraso == 1 ? " dia" : " dias");
    }
}
