package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.sst.application.dto.ApuracaoMetaPeriodo;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository.RealizacaoInspetorProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SstAnalyticsUseCasesTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    @Mock InspecaoSegurancaRepository repository;
    @Mock ApurarMetasPeriodoUseCase apurarMetas;
    @Mock EquipeApi equipeApi;
    @Mock ColaboradorApi colaboradorApi;
    @Mock UsuarioApi usuarioApi;

    @InjectMocks SstAnalyticsUseCases useCases;

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();

    private RealizacaoInspetorProjection realizacao(UUID inspetor, LocalDate data) {
        OffsetDateTime momento = data.atStartOfDay(ZONA).plusHours(9).toOffsetDateTime();
        return new RealizacaoInspetorProjection() {
            @Override
            public UUID getIdInspetor() {
                return inspetor;
            }

            @Override
            public UUID getIdTipoInspecao() {
                return UUID.randomUUID();
            }

            @Override
            public OffsetDateTime getDataRealizacao() {
                return momento;
            }
        };
    }

    @Test
    void mediaMensalIncluiMesesZeradosNoDenominador() {
        when(repository.countAtivasNaoCanceladasNoPeriodo(any(), any())).thenReturn(25L);

        var result = useCases.calcularMediaMensalInspecoes(LocalDate.of(2026, 7, 20), 12);

        assertThat(result.quantidadeMeses()).isEqualTo(12);
        assertThat(result.totalInspecoes()).isEqualTo(25);
        assertThat(result.mediaMensal()).isEqualByComparingTo(new BigDecimal("2.08"));
        assertThat(result.mesInicial()).isEqualTo(LocalDate.of(2025, 8, 1));
        assertThat(result.mesFinal()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void atingimentoDeMetasComparaRealizadoComMetaRateada() {
        when(apurarMetas.apurar(any(), any(), any(), any(), any())).thenReturn(new ApuracaoMetaPeriodo(
                new BigDecimal("6.000000"),
                Map.of(),
                Map.of(ana, new BigDecimal("2.000000"), bruno, new BigDecimal("4.000000")),
                2, 0));
        when(repository.listarRealizacoesPorInspetor(anyCollection(), any(), any())).thenReturn(List.of(
                realizacao(ana, LocalDate.of(2026, 8, 3)),
                realizacao(ana, LocalDate.of(2026, 8, 5)),
                realizacao(bruno, LocalDate.of(2026, 8, 7))));
        when(repository.countAtivasNaoCanceladasNoPeriodo(any(), any())).thenReturn(3L);
        when(colaboradorApi.buscarNomeCompletoPorUsuario(ana)).thenReturn(Optional.of("ANA SILVA"));
        when(colaboradorApi.buscarNomeCompletoPorUsuario(bruno)).thenReturn(Optional.of("BRUNO SOUZA"));

        var result = useCases.calcularAtingimentoMetasInspecoes(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 10);

        assertThat(result.metrica()).isEqualTo("sst.inspecoes.atingimento_metas");
        assertThat(result.possuiMeta()).isTrue();
        assertThat(result.metaPeriodo()).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(result.totalRealizado()).isEqualTo(3);
        assertThat(result.percentualAtingimento()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(result.colaboradoresComMeta()).isEqualTo(2);
        assertThat(result.inspetoresNaMeta()).isEqualTo(1);

        // Ordenado do pior para o melhor atingimento: Bruno (25%) antes de Ana (100%)
        assertThat(result.inspetores()).hasSize(2);
        assertThat(result.inspetores().get(0).nomeInspetor()).isEqualTo("BRUNO SOUZA");
        assertThat(result.inspetores().get(0).percentualAtingimento()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.inspetores().get(0).atingiuMeta()).isFalse();
        assertThat(result.inspetores().get(1).nomeInspetor()).isEqualTo("ANA SILVA");
        assertThat(result.inspetores().get(1).atingiuMeta()).isTrue();
    }

    @Test
    void atingimentoSemMetaCadastradaNaoDividePorZero() {
        when(apurarMetas.apurar(any(), any(), any(), any(), any())).thenReturn(ApuracaoMetaPeriodo.vazia());
        when(repository.countAtivasNaoCanceladasNoPeriodo(any(), any())).thenReturn(7L);

        var result = useCases.calcularAtingimentoMetasInspecoes(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 10);

        assertThat(result.possuiMeta()).isFalse();
        assertThat(result.percentualAtingimento()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totalRealizado()).isEqualTo(7);
        assertThat(result.inspetores()).isEmpty();
    }

    @Test
    void coberturaComparaInspecionadosComEfetivoAtivo() {
        when(repository.contarColaboradoresInspecionadosNoPeriodo(any(), any())).thenReturn(30L);
        when(repository.countAtivasNaoCanceladasNoPeriodo(any(), any())).thenReturn(45L);
        when(colaboradorApi.contarAtivos(any(), any())).thenReturn(120L);

        var result = useCases.calcularCoberturaColaboradores(
                OffsetDateTime.parse("2026-08-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-09-01T00:00:00-03:00"));

        assertThat(result.metrica()).isEqualTo("sst.colaboradores.cobertura_inspecoes");
        assertThat(result.colaboradoresInspecionados()).isEqualTo(30);
        assertThat(result.colaboradoresAtivos()).isEqualTo(120);
        assertThat(result.percentualCobertura()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.totalInspecoes()).isEqualTo(45);
    }
}
