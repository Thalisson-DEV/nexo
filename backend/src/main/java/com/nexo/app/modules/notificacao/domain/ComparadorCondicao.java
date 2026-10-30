package com.nexo.app.modules.notificacao.domain;

import java.math.BigDecimal;
import java.util.Arrays;

import org.springframework.util.StringUtils;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

/** Comparação segura compartilhada por condições de regra e de roteamento. */
public final class ComparadorCondicao {

    private ComparadorCondicao() {
    }

    public static void validar(OperadorCondicao operador, String valor) {
        if (operador == null) {
            throw new InvarianteVioladaException("Escolha o operador da condição.");
        }
        if (!StringUtils.hasText(valor)) {
            throw new InvarianteVioladaException("Informe o valor de comparação da condição.");
        }
        if (operador.exigeNumero() && paraNumero(valor) == null) {
            throw new InvarianteVioladaException(
                    "O operador '" + operador.getRotulo() + "' exige um número; '" + valor + "' não é.");
        }
    }

    public static boolean satisfeitaPor(OperadorCondicao operador, String esperado, Object valorDoContexto) {
        String atual = valorDoContexto == null ? null : String.valueOf(valorDoContexto).trim();
        return switch (operador) {
            case IGUAL -> igualA(esperado, atual);
            case DIFERENTE -> !igualA(esperado, atual);
            case CONTEM -> atual != null && atual.toLowerCase().contains(esperado.toLowerCase());
            case EM -> atual != null && Arrays.stream(esperado.split(","))
                    .map(String::trim)
                    .anyMatch(opcao -> opcao.equalsIgnoreCase(atual));
            case MAIOR_QUE -> compararNumeros(operador, esperado, atual) > 0;
            case MAIOR_OU_IGUAL -> compararNumeros(operador, esperado, atual) >= 0;
            case MENOR_QUE -> compararNumeros(operador, esperado, atual) < 0;
            case MENOR_OU_IGUAL -> compararNumeros(operador, esperado, atual) <= 0;
        };
    }

    private static boolean igualA(String esperado, String atual) {
        if (atual == null) {
            return false;
        }
        BigDecimal esperadoNumero = paraNumero(esperado);
        BigDecimal atualNumero = paraNumero(atual);
        if (esperadoNumero != null && atualNumero != null) {
            return esperadoNumero.compareTo(atualNumero) == 0;
        }
        return esperado.equalsIgnoreCase(atual);
    }

    private static int compararNumeros(OperadorCondicao operador, String esperado, String atual) {
        BigDecimal atualNumero = paraNumero(atual);
        if (atualNumero == null) {
            return operador == OperadorCondicao.MENOR_QUE
                    || operador == OperadorCondicao.MENOR_OU_IGUAL ? 1 : -1;
        }
        return atualNumero.compareTo(paraNumero(esperado));
    }

    private static BigDecimal paraNumero(String texto) {
        if (!StringUtils.hasText(texto)) {
            return null;
        }
        try {
            return new BigDecimal(texto.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
