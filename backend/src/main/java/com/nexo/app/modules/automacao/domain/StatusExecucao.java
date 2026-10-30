package com.nexo.app.modules.automacao.domain;

import java.util.Set;

public enum StatusExecucao {
    NA_FILA, EXECUTANDO, AGUARDANDO, SUCESSO, FALHA, CANCELADA, TEMPO_ESGOTADO;

    public static final Set<StatusExecucao> FINAIS = Set.of(SUCESSO, FALHA, CANCELADA, TEMPO_ESGOTADO);

    public boolean isFinal() {
        return FINAIS.contains(this);
    }
}
