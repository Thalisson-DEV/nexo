package com.nexo.app.modules.assistente.application;

import com.nexo.app.shared.application.analytics.SemanticQueryContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Captura, na thread da requisicao, a identidade e as permissoes do usuario para o agente. As ferramentas
 * recebem esse contexto explicitamente e nunca dependem do ThreadLocal do Spring Security.
 */
@Component
public class AssistantSecurityContextFactory {

    public SemanticQueryContext current(UUID usuarioId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> authorities = authentication == null || authentication.getAuthorities() == null
                ? Set.of()
                : authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .collect(Collectors.toUnmodifiableSet());
        return new SemanticQueryContext(usuarioId, authorities);
    }

    /** Nome de exibicao do usuario autenticado (sem acoplar ao modulo IAM). */
    public String nomeUsuario() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        for (String metodo : new String[]{"getNome", "getNomeCompleto", "getName"}) {
            try {
                Method method = principal.getClass().getMethod(metodo);
                Object valor = method.invoke(principal);
                if (valor instanceof String texto && !texto.isBlank()) return texto.strip();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // tenta o proximo
            }
        }
        return null;
    }
}
