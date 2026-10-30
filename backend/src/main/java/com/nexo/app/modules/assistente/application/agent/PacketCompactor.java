package com.nexo.app.modules.assistente.application.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compacta os pacotes de uma execucao para persistencia: deltas de texto consecutivos da mesma posicao viram
 * um unico delta, saidas consecutivas do mesmo fluxo (stdout/stderr) sao unidas e heartbeats sao descartados.
 * O replay no historico produz exatamente a mesma timeline exibida durante o streaming.
 */
public final class PacketCompactor {

    private static final int MAX_SAIDA_PERSISTIDA = 40_000;

    private PacketCompactor() {
    }

    public static List<Packet> compactar(List<Packet> pacotes) {
        List<Packet> compactados = new ArrayList<>();
        for (Packet pacote : pacotes) {
            String tipo = pacote.type();
            if (Packet.Types.HEARTBEAT.equals(tipo)) continue;
            Packet anterior = compactados.isEmpty() ? null : compactados.getLast();
            if (anterior != null && anterior.placement().equals(pacote.placement()) && anterior.type().equals(tipo)) {
                if (Packet.Types.MESSAGE_DELTA.equals(tipo)) {
                    compactados.set(compactados.size() - 1, juntar(anterior, pacote, "content", Integer.MAX_VALUE));
                    continue;
                }
                if (Packet.Types.TOOL_OUTPUT.equals(tipo)
                        && Objects.equals(anterior.obj().get("stream"), pacote.obj().get("stream"))
                        && Objects.equals(anterior.obj().get("toolCallId"), pacote.obj().get("toolCallId"))) {
                    compactados.set(compactados.size() - 1, juntar(anterior, pacote, "data", MAX_SAIDA_PERSISTIDA));
                    continue;
                }
            }
            compactados.add(pacote);
        }
        return compactados;
    }

    private static Packet juntar(Packet anterior, Packet atual, String campo, int limite) {
        String texto = Objects.toString(anterior.obj().get(campo), "") + Objects.toString(atual.obj().get(campo), "");
        if (texto.length() > limite) texto = texto.substring(0, limite) + "\n...[saida truncada]";
        Map<String, Object> obj = new LinkedHashMap<>(anterior.obj());
        obj.put(campo, texto);
        return new Packet(anterior.placement(), obj);
    }
}
