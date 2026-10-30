package com.nexo.app.modules.assistente.application.navigation;

import com.nexo.app.modules.assistente.application.dto.AcaoInterface;
import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NavigationActionResolver {

    private final NavigationActionCatalog catalog;

    public Resolution resolve(NavigationActionProposal proposal, SemanticQueryContext context) {
        if (proposal == null || !"NAVIGATE".equals(proposal.type())) return Resolution.rejected(Rejection.INVALID_TYPE);
        Optional<NavigationActionProperties.Entry> authorized = catalog.findAuthorized(proposal.destinationKey(), context);
        if (authorized.isEmpty()) return Resolution.rejected(Rejection.UNAVAILABLE_KEY);
        NavigationActionProperties.Entry entry = authorized.get();

        Map<String, NavigationActionProperties.Parameter> definitions = new LinkedHashMap<>();
        entry.parameters().forEach(parameter -> definitions.put(parameter.name(), parameter));
        if (proposal.parameters().keySet().stream().anyMatch(key -> !definitions.containsKey(key))) {
            return Resolution.rejected(Rejection.INVALID_PARAMETERS);
        }
        if (entry.parameters().stream().anyMatch(parameter -> parameter.required()
                && !proposal.parameters().containsKey(parameter.name()))) {
            return Resolution.rejected(Rejection.MISSING_PARAMETERS);
        }

        Map<String, Object> converted = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, Object> item : proposal.parameters().entrySet()) {
                converted.put(item.getKey(), convert(definitions.get(item.getKey()), item.getValue()));
            }
        } catch (IllegalArgumentException exception) {
            return Resolution.rejected(Rejection.INVALID_PARAMETERS);
        }

        return Resolution.accepted(new AcaoInterface(
                "NAVIGATE", entry.key(), entry.defaultLabel(), entry.publicAuthority(), converted));
    }

    private Object convert(NavigationActionProperties.Parameter definition, Object raw) {
        if (raw == null) throw new IllegalArgumentException("Parametro nulo");
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || text.length() > definition.maxLength()) throw new IllegalArgumentException("Tamanho invalido");
        return switch (definition.type()) {
            case UUID -> UUID.fromString(text).toString();
            case ENUM -> definition.allowedValues().stream()
                    .filter(allowed -> allowed.equalsIgnoreCase(text))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Enum invalido"));
            case INTEGER -> Long.parseLong(text);
            case DECIMAL -> new BigDecimal(text);
            case DATE -> LocalDate.parse(text).toString();
            case TEXT -> safeText(text);
        };
    }

    private String safeText(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (value.contains("/") || value.contains("\\") || normalized.contains("..")
                || normalized.contains("://") || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Texto inseguro");
        }
        return value;
    }

    public enum Rejection {
        INVALID_TYPE,
        UNAVAILABLE_KEY,
        MISSING_PARAMETERS,
        INVALID_PARAMETERS
    }

    public record Resolution(Optional<AcaoInterface> action, Rejection rejection) {
        public static Resolution accepted(AcaoInterface action) {
            return new Resolution(Optional.of(action), null);
        }

        public static Resolution rejected(Rejection reason) {
            return new Resolution(Optional.empty(), reason);
        }

        public boolean accepted() {
            return action.isPresent();
        }
    }
}
