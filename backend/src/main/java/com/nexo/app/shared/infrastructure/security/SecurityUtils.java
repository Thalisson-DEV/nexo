package com.nexo.app.shared.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;
import java.lang.reflect.Method;

/**
 * Utilitário para operações de segurança desacopladas de módulos específicos.
 */
public class SecurityUtils {

    /**
     * Extrai o ID do usuário logado do SecurityContext de forma agnóstica ao módulo IAM.
     * Utiliza reflexão para evitar dependência direta da entidade Usuario nos módulos de negócio.
     */
    public static UUID getUsuarioLogadoId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }

        Object principal = auth.getPrincipal();

        // Se o principal for uma String, tentamos converter para UUID
        if (principal instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Se for um objeto (como a entidade Usuario) o método getId() via reflexão
        try {
            Method getIdMethod = principal.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(principal);
            if (id instanceof UUID uuid) {
                return uuid;
            } else if (id instanceof String s) {
                return UUID.fromString(s);
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    /**
     * Indica se o usuário autenticado possui a authority informada.
     *
     * <p>Complementa o {@code @PreAuthorize} dos endpoints para os casos em que a
     * permissão exigida só é conhecida em tempo de execução — como a alçada de
     * aprovação, que depende do valor da solicitação.</p>
     */
    public static boolean possuiAutoridade(String autoridade) {
        if (autoridade == null || autoridade.isBlank()) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(concedida -> autoridade.equals(concedida.getAuthority()));
    }
}
