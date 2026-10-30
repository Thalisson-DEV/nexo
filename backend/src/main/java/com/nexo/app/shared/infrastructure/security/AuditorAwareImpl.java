package com.nexo.app.shared.infrastructure.security;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * Captura o utilizador autenticado para preencher automaticamente os campos
 * usuario_criacao e usuario_ultima_alteracao na BaseEntity.
 *
 * @author <b>Thalisson<b/>
 * @version <b>1.0<b/>
 */
@Component("auditorProvider")
public class AuditorAwareImpl implements AuditorAware<String> {

    @NotNull
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || Objects.equals(authentication.getPrincipal(), "anonymousUser")) {
            return Optional.of("SYSTEM");
        }

        return Optional.of(authentication.getName());
    }
}