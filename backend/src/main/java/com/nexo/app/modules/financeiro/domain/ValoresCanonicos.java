package com.nexo.app.modules.financeiro.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Valores tipados dos campos canônicos de uma solicitação, já convertidos e
 * validados contra o schema do formulário.
 *
 * <p>Serve de fronteira entre o payload cru (chave/valor vindo do frontend) e o
 * domínio: a partir daqui nenhum código precisa converter ou desconfiar do tipo.
 * Chaves ausentes representam campos não presentes na variante — distintas de
 * chaves presentes com valor nulo, que representam limpeza deliberada.</p>
 */
public record ValoresCanonicos(Map<CampoCanonico, Object> valores) {

    public ValoresCanonicos {
        valores = valores == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(valores));
    }

    public static ValoresCanonicos vazio() {
        return new ValoresCanonicos(Map.of());
    }

    public boolean contem(CampoCanonico campo) {
        return valores.containsKey(campo);
    }

    public String texto(CampoCanonico campo) {
        return (String) valores.get(campo);
    }

    public UUID referencia(CampoCanonico campo) {
        return (UUID) valores.get(campo);
    }

    public BigDecimal decimal(CampoCanonico campo) {
        return (BigDecimal) valores.get(campo);
    }

    public Integer inteiro(CampoCanonico campo) {
        Object valor = valores.get(campo);
        return valor == null ? null : ((Number) valor).intValue();
    }

    public LocalDate data(CampoCanonico campo) {
        return (LocalDate) valores.get(campo);
    }

    public Boolean logico(CampoCanonico campo) {
        return (Boolean) valores.get(campo);
    }

    /** Converte o nome persistido de volta para a constante da enumeração. */
    public <E extends Enum<E>> E enumerado(CampoCanonico campo, Class<E> tipo) {
        Object valor = valores.get(campo);
        if (valor == null) {
            return null;
        }
        if (tipo.isInstance(valor)) {
            return tipo.cast(valor);
        }
        return Enum.valueOf(tipo, valor.toString());
    }
}
