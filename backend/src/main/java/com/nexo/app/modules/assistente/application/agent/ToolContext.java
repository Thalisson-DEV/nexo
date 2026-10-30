package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;

import java.util.Objects;
import java.util.UUID;

/** Contexto de uma chamada de ferramenta: execucao, identificacao da chamada e posicao na timeline. */
public record ToolContext(AgentRunContext run, String toolCallId, Packet.Placement placement, int cycle) {

    public ToolContext {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(placement, "placement");
    }

    public void emit(String type, Object... keyValues) {
        Object[] withId = new Object[keyValues.length + 2];
        withId[0] = "toolCallId";
        withId[1] = toolCallId;
        System.arraycopy(keyValues, 0, withId, 2, keyValues.length);
        run.emit(Packet.of(placement, type, withId));
    }

    public SemanticQueryContext security() {
        return run.security();
    }

    public UUID usuarioId() {
        return run.usuarioId();
    }

    public UUID conversaId() {
        return run.conversaId();
    }

    public boolean cancelled() {
        return run.cancelled();
    }
}
