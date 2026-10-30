package com.nexo.app.shared.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erro de preenchimento de um formulário dinâmico, acumulando <b>todos</b> os
 * problemas encontrados em vez de apenas o primeiro.
 *
 * <p>Reportar campo a campo permite ao frontend destacar de uma vez tudo o que
 * precisa ser corrigido, em vez de revelar um erro por submissão.</p>
 *
 * @author <b>Thalisson</b>
 */
@Getter
public class PreenchimentoInvalidoException extends BaseBusinessException {

    /** Chave do campo → mensagem do problema encontrado nele. */
    private final Map<String, String> erros;

    public PreenchimentoInvalidoException(Map<String, String> erros, HttpStatus status) {
        super("Preenchimento invalido", montarResumo(erros), status, "preenchimento-invalido");
        this.erros = Collections.unmodifiableMap(new LinkedHashMap<>(erros));
    }

    public PreenchimentoInvalidoException(Map<String, String> erros) {
        super("Preenchimento Inválido",
                montarResumo(erros),
                HttpStatus.UNPROCESSABLE_CONTENT,
                "preenchimento-invalido");
        this.erros = Collections.unmodifiableMap(new LinkedHashMap<>(erros));
    }

    private static String montarResumo(Map<String, String> erros) {
        if (erros == null || erros.isEmpty()) {
            return "O formulário possui campos inválidos.";
        }
        if (erros.size() == 1) {
            return erros.values().iterator().next();
        }
        return "O formulário possui " + erros.size() + " campos com problemas. Confira o detalhamento por campo.";
    }
}
