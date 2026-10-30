package com.nexo.app.modules.assistente.application.navigation;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NavigationActionCatalog {

    private final NavigationActionProperties properties;
    private final Map<String, NavigationActionProperties.Entry> byKey;

    public NavigationActionCatalog(NavigationActionProperties properties) {
        this.properties = properties;
        Map<String, NavigationActionProperties.Entry> indexed = new LinkedHashMap<>();
        for (NavigationActionProperties.Entry entry : properties.entries()) {
            if (entry.state() == NavigationActionState.ATIVO && entry.requiredAuthorities().isEmpty()) {
                throw new IllegalArgumentException(
                        "Acao de navegacao ativa exige autoridade: " + entry.key());
            }
            if (indexed.putIfAbsent(entry.key(), entry) != null) {
                throw new IllegalArgumentException("Chave duplicada no catalogo de navegacao: " + entry.key());
            }
        }
        this.byKey = Map.copyOf(indexed);
    }

    public String version() {
        return properties.version();
    }

    public List<NavigationActionProperties.Entry> listAuthorized(SemanticQueryContext context) {
        return properties.entries().stream()
                .filter(entry -> entry.state() == NavigationActionState.ATIVO)
                .filter(entry -> entry.requiredAuthorities().stream().allMatch(context::possuiAutoridade))
                .toList();
    }

    public Optional<NavigationActionProperties.Entry> findAuthorized(
            String key,
            SemanticQueryContext context) {
        if (key == null) return Optional.empty();
        NavigationActionProperties.Entry entry = byKey.get(key);
        if (entry == null || entry.state() != NavigationActionState.ATIVO) return Optional.empty();
        if (!entry.requiredAuthorities().stream().allMatch(context::possuiAutoridade)) return Optional.empty();
        return Optional.of(entry);
    }
}
