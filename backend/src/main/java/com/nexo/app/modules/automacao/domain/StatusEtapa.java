package com.nexo.app.modules.automacao.domain;

import java.util.Set;

public enum StatusEtapa {
    EXECUTANDO, AGUARDANDO, SUCESSO, FALHA, IGNORADA, TEMPO_ESGOTADO, CANCELADA;

    public static final Set<StatusEtapa> FINAIS = Set.of(SUCESSO, FALHA, IGNORADA, TEMPO_ESGOTADO, CANCELADA);

    /** O nome usado em "Executar após" e em {{ steps.x.status }}. */
    public String situacao() {
        return switch (this) {
            case SUCESSO -> "succeeded";
            case IGNORADA -> "skipped";
            case TEMPO_ESGOTADO -> "timed_out";
            default -> "failed";
        };
    }

    public boolean isFinal() {
        return FINAIS.contains(this);
    }
}
