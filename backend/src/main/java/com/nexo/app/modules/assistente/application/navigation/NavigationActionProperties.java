package com.nexo.app.modules.assistente.application.navigation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ces.assistente.navigation")
public record NavigationActionProperties(
        String version,
        List<Entry> entries) {

    public NavigationActionProperties {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Versao do catalogo obrigatoria");
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(
            String key,
            String modelDescription,
            String defaultLabel,
            List<String> requiredAuthorities,
            String publicAuthority,
            List<Parameter> parameters,
            NavigationActionState state) {

        public Entry {
            if (key == null || !key.matches("[A-Z][A-Z0-9_]*")) {
                throw new IllegalArgumentException("Chave de navegacao invalida: " + key);
            }
            if (modelDescription == null || modelDescription.isBlank()) {
                throw new IllegalArgumentException("Descricao da acao obrigatoria: " + key);
            }
            if (defaultLabel == null || defaultLabel.isBlank()) {
                throw new IllegalArgumentException("Rotulo da acao obrigatorio: " + key);
            }
            requiredAuthorities = requiredAuthorities == null ? List.of() : List.copyOf(requiredAuthorities);
            publicAuthority = publicAuthority == null || publicAuthority.isBlank()
                    ? requiredAuthorities.stream().findFirst().orElse("")
                    : publicAuthority;
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
            state = state == null ? NavigationActionState.BLOQUEADO : state;
        }
    }

    public record Parameter(
            String name,
            NavigationParameterType type,
            NavigationParameterDestination frontendDestination,
            boolean required,
            List<String> allowedValues,
            Integer maxLength) {

        public Parameter {
            if (name == null || !name.matches("[a-z][A-Za-z0-9]*")) {
                throw new IllegalArgumentException("Nome de parametro invalido: " + name);
            }
            if (type == null || frontendDestination == null) {
                throw new IllegalArgumentException("Tipo e destino do parametro sao obrigatorios: " + name);
            }
            allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
            maxLength = maxLength == null ? 200 : maxLength;
            if (maxLength < 1 || maxLength > 1000) throw new IllegalArgumentException("Limite de parametro invalido");
            if (type == NavigationParameterType.ENUM && allowedValues.isEmpty()) {
                throw new IllegalArgumentException("Parametro ENUM exige valores permitidos: " + name);
            }
        }
    }
}
