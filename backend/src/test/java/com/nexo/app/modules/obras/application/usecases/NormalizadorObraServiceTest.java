package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.domain.TipoObra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NormalizadorObraServiceTest {

    private NormalizadorObraService normalizador;

    @BeforeEach
    void setUp() {
        normalizador = new NormalizadorObraService();
    }

    @ParameterizedTest(name = "Entrada: ''{0}'' -> Esperado: ''{1}''")
    @CsvSource({
            "B-1228694, 1228694",
            "B-1231009, 1231009",
            "b-1231258, 1231258",
            "B1231258, 1231258",
            "1214623, 1214623",
            "0994094, 0994094",
            "B-0994094, 0994094",
            "B-SOL1290, B-SOL1290",
            "SOL1290, SOL1290",
            "'  B-1228694  ', 1228694"
    })
    @DisplayName("Deve normalizar números de obra removendo prefixos quando seguidos apenas de dígitos e preservando zeros e exceções")
    void deveNormalizarNumeroObra(String entrada, String esperado) {
        String resultado = normalizador.normalizarNumero(entrada);
        assertThat(resultado).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Deve retornar null para número nulo ou em branco")
    void deveRetornarNullParaNumeroNuloOuEmBranco() {
        assertThat(normalizador.normalizarNumero(null)).isNull();
        assertThat(normalizador.normalizarNumero("")).isNull();
        assertThat(normalizador.normalizarNumero("   ")).isNull();
    }

    @Test
    @DisplayName("Deve normalizar título convertendo para maiúsculo e removendo espaços duplicados")
    void deveNormalizarTitulo() {
        String entrada = "  rede   pedagogica remanso (concessionaria)  ";
        String resultado = normalizador.normalizarTitulo(entrada);
        assertThat(resultado).isEqualTo("REDE PEDAGOGICA REMANSO (CONCESSIONARIA)");
    }

    @ParameterizedTest
    @EnumSource(TipoObra.class)
    @DisplayName("Deve aceitar toda a matriz de tipos de obra")
    void deveAceitarMatrizTipoObra(TipoObra tipoObra) {
        assertThat(normalizador.parseTipoObra("  " + tipoObra.name().toLowerCase() + "  "))
                .isEqualTo(tipoObra);
    }

    @Test
    @DisplayName("Deve assumir OBRA quando o tipo não vier no contrato legado")
    void deveAssumirObraParaTipoAusente() {
        assertThat(normalizador.parseTipoObra(null)).isEqualTo(TipoObra.OBRA);
        assertThat(normalizador.parseTipoObra(" ")).isEqualTo(TipoObra.OBRA);
    }

    @Test
    @DisplayName("Deve rejeitar tipo de obra desconhecido")
    void deveRejeitarTipoObraDesconhecido() {
        assertThatThrownBy(() -> normalizador.parseTipoObra("EMERGENCIAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBRA, CORRETIVA, PREVENTIVA ou LPT");
    }

    @Test
    @DisplayName("Deve fazer parsing correto de data no padrão brasileiro dd/MM/yyyy")
    void deveFazerParsingDataBr() {
        LocalDate data = normalizador.parseData("11/03/2025");
        assertThat(data).isEqualTo(LocalDate.of(2025, 3, 11));
    }

    @Test
    @DisplayName("Deve fazer parsing correto de data no padrão ISO yyyy-MM-dd")
    void deveFazerParsingDataIso() {
        LocalDate data = normalizador.parseData("2025-11-30");
        assertThat(data).isEqualTo(LocalDate.of(2025, 11, 30));
    }

    @Test
    @DisplayName("Deve lançar exceção para formato de data inválido")
    void deveLancarExcecaoParaDataInvalida() {
        assertThatThrownBy(() -> normalizador.parseData("data_invalida"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Data inválida");
    }

    @Test
    @DisplayName("Deve tratar coordenadas válidas e ignorar zeros/máscaras corrompidas")
    void deveTratarCoordenadas() {
        assertThat(normalizador.parseCoordenada(null)).isNull();
        assertThat(normalizador.parseCoordenada("0")).isNull();
        assertThat(normalizador.parseCoordenada("-00.00000")).isNull();
        assertThat(normalizador.parseCoordenada("0.0")).isNull();
        assertThat(normalizador.parseCoordenada("-419.682.287.424")).isNull(); // múltiplos pontos descartados
        assertThat(normalizador.parseCoordenada("-41.968228")).isEqualTo(-41.968228);
        assertThat(normalizador.parseCoordenada("-9,439007")).isEqualTo(-9.439007);
    }

    @Test
    @DisplayName("Deve fazer parsing de valores monetários com diferentes formatos")
    void deveFazerParsingValoresMonetarios() {
        assertThat(normalizador.parseValor(null)).isNull();
        assertThat(normalizador.parseValor("15000.00")).isEqualTo(new BigDecimal("15000.00"));
        assertThat(normalizador.parseValor("15.000,50")).isEqualTo(new BigDecimal("15000.50"));
        assertThat(normalizador.parseValor("R$ 1.250.000,00")).isEqualTo(new BigDecimal("1250000.00"));
    }
}
