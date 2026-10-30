package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FichaVeiculoTest {

    private static DadosFichaVeiculo dados(RegimeVeiculo regime, String locadora, ProvedorTelemetria provedor,
                                           String rastreador, Integer anoFab, Integer anoMod) {
        return new DadosFichaVeiculo(null, anoFab, anoMod, "9bwzzz377vt004251", "12345678901", "branco",
                Combustivel.DIESEL_S10, new BigDecimal("80"), new BigDecimal("8.5"), SituacaoOperacional.ATIVO,
                regime, locadora, new BigDecimal("4500"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                provedor, rastreador, null);
    }

    @Test
    void provisionaFichaVaziaComProjecaoDoRh() {
        UUID base = UUID.randomUUID();
        var ficha = FichaVeiculo.provisionar(UUID.randomUUID(), "abc1d23", base);

        assertThat(ficha.getPlaca()).isEqualTo("ABC1D23");
        assertThat(ficha.getIdBaseOperacional()).isEqualTo(base);
        assertThat(ficha.getSituacao()).isEqualTo(SituacaoOperacional.ATIVO);
        assertThat(ficha.getRegime()).isEqualTo(RegimeVeiculo.PROPRIO);
    }

    @Test
    void veiculoProprioDescartaDadosDeLocacao() {
        var ficha = FichaVeiculo.provisionar(UUID.randomUUID(), "ABC1D23", null);

        ficha.atualizar(dados(RegimeVeiculo.PROPRIO, "Locadora X", null, null, 2024, 2025));

        assertThat(ficha.getLocadora()).isNull();
        assertThat(ficha.getCustoMensalLocacao()).isNull();
        assertThat(ficha.getChassi()).isEqualTo("9BWZZZ377VT004251");
    }

    @Test
    void veiculoLocadoExigeLocadora() {
        var ficha = FichaVeiculo.provisionar(UUID.randomUUID(), "ABC1D23", null);

        assertThatThrownBy(() -> ficha.atualizar(dados(RegimeVeiculo.LOCADO, null, null, null, 2024, 2025)))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("locadora");

        ficha.atualizar(dados(RegimeVeiculo.LOCADO, "Locadora X", null, null, 2024, 2025));
        assertThat(ficha.isLocado()).isTrue();
        assertThat(ficha.getCustoMensalLocacao()).isEqualByComparingTo("4500");
    }

    @Test
    void telemetriaExigeProvedorERastreadorJuntos() {
        var ficha = FichaVeiculo.provisionar(UUID.randomUUID(), "ABC1D23", null);

        assertThatThrownBy(() -> ficha.atualizar(dados(RegimeVeiculo.PROPRIO, null, ProvedorTelemetria.ZUQ, null, 2024, 2025)))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void anoModeloDeveSerOMesmoOuOSeguinteAoDeFabricacao() {
        var ficha = FichaVeiculo.provisionar(UUID.randomUUID(), "ABC1D23", null);

        assertThatThrownBy(() -> ficha.atualizar(dados(RegimeVeiculo.PROPRIO, null, null, null, 2024, 2026)))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    @Test
    void sincronizarCadastroAcompanhaDesativacaoDoRh() {
        var ficha = FichaVeiculo.provisionar(UUID.randomUUID(), "ABC1D23", null);

        ficha.sincronizarCadastro("XYZ9K88", null, false);

        assertThat(ficha.getPlaca()).isEqualTo("XYZ9K88");
        assertThat(ficha.isAtivo()).isFalse();
    }
}
