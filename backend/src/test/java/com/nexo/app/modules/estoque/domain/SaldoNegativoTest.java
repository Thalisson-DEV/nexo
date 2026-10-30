package com.nexo.app.modules.estoque.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaldoNegativoTest {

    @Test
    void permiteDebitoMaiorQueDisponivelESinalizaDivergencia() {
        Estoque estoque = mock(Estoque.class);
        Material material = mock(Material.class);
        when(estoque.isAtivo()).thenReturn(true);
        when(material.isAtivo()).thenReturn(true);
        Saldo saldo = new Saldo(estoque, material, CondicaoMaterial.NOVO);
        saldo.adicionarQuantidade(new BigDecimal("2.000"));

        saldo.removerQuantidade(new BigDecimal("5.000"));

        assertThat(saldo.getQuantidade()).isEqualByComparingTo("-3.000");
        assertThat(saldo.isNegativo()).isTrue();
    }

    @Test
    void continuaRejeitandoQuantidadeNaoPositiva() {
        Estoque estoque = mock(Estoque.class);
        Material material = mock(Material.class);
        when(estoque.isAtivo()).thenReturn(true);
        when(material.isAtivo()).thenReturn(true);
        Saldo saldo = new Saldo(estoque, material, CondicaoMaterial.NOVO);

        assertThatThrownBy(() -> saldo.removerQuantidade(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
