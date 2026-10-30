package com.nexo.app.modules.rh.infrastructure.persistence;

import java.util.UUID;

/**
 * Projeção mínima para o autocomplete de colaboradores.
 *
 * <p>{@code nomeCompleto} continua sendo uma propriedade da entidade e, por
 * isso, a conversão JPA ({@code CryptoConverterImpl}) é aplicada ao ler o
 * valor. A consulta não materializa {@code Colaborador} nem seus vínculos.</p>
 */
public interface ColaboradorLookupProjection {

    UUID getId();

    String getNomeCompleto();

    String getNomeBusca();
}
