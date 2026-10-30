package com.nexo.app.modules.assistente.application.navigation;

import com.nexo.app.modules.assistente.application.dto.AcaoInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Coleta as acoes validadas numa execucao — navegacao e filtros de painel (ferramentas podem rodar em paralelo). */
public final class NavigationActionCollector {

    private static final int MAX_ACTIONS = 3;
    private final List<AcaoInterface> actions = new ArrayList<>();

    public synchronized boolean record(AcaoInterface action) {
        if (action == null || actions.size() >= MAX_ACTIONS) return false;
        boolean duplicate = actions.stream().anyMatch(existing ->
                Objects.equals(existing.tipo(), action.tipo())
                        && Objects.equals(existing.chaveDestino(), action.chaveDestino())
                        && Objects.equals(existing.alvo(), action.alvo())
                        && existing.parametros().equals(action.parametros())
                        && Objects.equals(existing.filtros(), action.filtros()));
        if (!duplicate) actions.add(action);
        return !duplicate;
    }

    public synchronized List<AcaoInterface> snapshot() {
        return List.copyOf(actions);
    }
}
