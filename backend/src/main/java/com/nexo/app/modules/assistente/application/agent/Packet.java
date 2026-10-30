package com.nexo.app.modules.assistente.application.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Unidade do protocolo de streaming do Copiloto (adaptado do Onyx: {@code {placement, obj}} em NDJSON).
 *
 * <p>{@code placement.turnIndex} identifica o ciclo do agente; {@code tabIndex} 0 e o texto do modelo e
 * 1..n sao as ferramentas executadas em paralelo naquele ciclo. {@code obj.type} discrimina o pacote.</p>
 */
public record Packet(Placement placement, Map<String, Object> obj) {

    public Packet {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(obj, "obj");
    }

    public String type() {
        Object type = obj.get("type");
        return type == null ? "" : type.toString();
    }

    public static Packet of(Placement placement, String type, Object... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("Pares chave/valor incompletos");
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("type", type);
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) obj.put(String.valueOf(keyValues[i]), value);
        }
        return new Packet(placement, obj);
    }

    public record Placement(int turnIndex, int tabIndex) {
        public static final Placement ROOT = new Placement(0, 0);
    }

    /** Tipos de pacote conhecidos pelo frontend. */
    public static final class Types {
        public static final String RUN_INFO = "run_info";
        public static final String MESSAGE_START = "message_start";
        public static final String MESSAGE_DELTA = "message_delta";
        public static final String TOOL_START = "tool_start";
        public static final String TOOL_OUTPUT = "tool_output";
        public static final String TOOL_RESULT = "tool_result";
        public static final String ARTIFACT = "artifact";
        public static final String FILE = "file";
        public static final String NAVIGATION = "navigation";
        public static final String ACTION_PROPOSAL = "action_proposal";
        public static final String SECTION_END = "section_end";
        public static final String TITLE = "title";
        public static final String ERROR = "error";
        public static final String STOP = "stop";
        public static final String HEARTBEAT = "heartbeat";

        private Types() {
        }
    }
}
