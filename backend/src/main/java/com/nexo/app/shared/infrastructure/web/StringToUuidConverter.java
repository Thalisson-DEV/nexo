package com.nexo.app.shared.infrastructure.web;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Conversor customizado para UUID que trata strings como "undefined" ou "null" como nulas.
 * Isso evita exceções de MethodArgumentTypeMismatchException quando o frontend (Angular)
 * envia valores não inicializados em parâmetros de busca.
 */
@Component
public class StringToUuidConverter implements Converter<String, UUID> {

    @Override
    public UUID convert(@NonNull String source) {
        if (source.isBlank() || "undefined".equalsIgnoreCase(source) || "null".equalsIgnoreCase(source)) {
            return null;
        }
        try {
            return UUID.fromString(source);
        } catch (IllegalArgumentException e) {
            // Se não for um UUID válido e não for "undefined", deixa o Spring lançar a exceção original
            // ou poderíamos retornar null. Para manter a segurança, vamos apenas tratar os casos conhecidos.
            throw e;
        }
    }
}
