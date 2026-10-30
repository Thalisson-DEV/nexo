package com.nexo.app.modules.assistente.application.navigation;

import java.util.Map;

public record NavigationActionProposal(
        String type,
        String destinationKey,
        Map<String, Object> parameters) {

    public NavigationActionProposal {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
