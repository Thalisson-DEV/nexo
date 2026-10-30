package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Composicao;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.ConsultaTurnos;
import com.nexo.app.modules.execucao.application.dto.LancamentoDoTurno;
import com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import com.nexo.app.modules.rh.EquipeApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.nexo.app.modules.dashboard.application.usecases.ConsultarTurnosUseCaseTest.turno;
import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCaseTest.equipe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetalharTurnoUseCaseTest {

    // Sexta-feira, 09/10/2026, 15h em São Paulo.
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-09T15:00:00-03:00");

    @Test
    @DisplayName("Turno encerrado: ritmo, meta, tempo até a primeira nota e maior trecho sem lançar")
    void indicadoresDeTurnoEncerrado() {
        TurnoResponse turno = turno(UUID.randomUUID(), "2026-10-09T07:00:00-03:00", "2026-10-09T13:00:00-03:00", 2);
        List<LancamentoDoTurno> lancamentos = List.of(
                lancamento("2026-10-09T07:30:00-03:00", "300", true),
                lancamento("2026-10-09T08:00:00-03:00", "200", false),
                lancamento("2026-10-09T11:00:00-03:00", "100", true),
                // Editada: não conta na produção, mas conta como lançamento sem localização.
                new LancamentoDoTurno(UUID.randomUUID(), "N-9", OffsetDateTime.parse("2026-10-09T12:00:00-03:00"),
                        StatusExecucao.EDITADO, new BigDecimal("999"), null, null));

        var ind = DetalharTurnoUseCase.indicadores(turno, lancamentos, new BigDecimal("1000"), AGORA);

        assertThat(ind.emAndamento()).isFalse();
        assertThat(ind.duracaoMinutos()).isEqualTo(360);
        assertThat(ind.notas()).isEqualTo(3);
        assertThat(ind.producao()).isEqualByComparingTo("600");
        assertThat(ind.valorMedioNota()).isEqualByComparingTo("200");
        assertThat(ind.producaoPorHora()).isEqualByComparingTo("100");
        assertThat(ind.percentualMetaDiaria()).isEqualByComparingTo("60.0");
        assertThat(ind.minutosAtePrimeiroLancamento()).isEqualTo(30);
        assertThat(ind.minutosAposUltimoLancamento()).isEqualTo(120);
        // 08:00 → 11:00 é o maior trecho, maior que 11:00 → 13:00.
        assertThat(ind.maiorIntervaloSemLancamentoMinutos()).isEqualTo(180);
        assertThat(ind.lancamentosSemLocalizacao()).isEqualTo(2);
    }

    @Test
    @DisplayName("Turno aberto desde ontem: tempos correm até agora e o turno é sinalizado")
    void turnoEsquecido() {
        TurnoResponse turno = turno(UUID.randomUUID(), "2026-10-08T19:00:00-03:00", null, 0);

        var ind = DetalharTurnoUseCase.indicadores(turno, List.of(), null, AGORA);

        assertThat(ind.emAndamento()).isTrue();
        assertThat(ind.abertoDesdeOutroDia()).isTrue();
        assertThat(ind.duracaoMinutos()).isEqualTo(20 * 60);
        assertThat(ind.notas()).isZero();
        assertThat(ind.minutosAtePrimeiroLancamento()).isNull();
        assertThat(ind.maiorIntervaloSemLancamentoMinutos()).isNull();
        assertThat(ind.percentualMetaDiaria()).isNull();
    }

    @Test
    @DisplayName("Histórico: médias por turno dos turnos encerrados anteriores; vazio sem turno anterior")
    void historico() {
        UUID idEquipe = UUID.randomUUID();
        TurnoResponse t1 = turno(idEquipe, "2026-10-01T07:00:00-03:00", "2026-10-01T15:00:00-03:00", 0);
        TurnoResponse t2 = turno(idEquipe, "2026-10-02T07:00:00-03:00", "2026-10-02T11:00:00-03:00", 0);

        var historico = DetalharTurnoUseCase.historico(List.of(t1, t2), List.of(
                new ProducaoDoTurno(t1.id(), 4L, new BigDecimal("800"), null, null),
                new ProducaoDoTurno(t2.id(), 1L, new BigDecimal("200"), null, null)));

        assertThat(historico.turnos()).isEqualTo(2);
        assertThat(historico.mediaNotasPorTurno()).isEqualByComparingTo("2.5");
        assertThat(historico.mediaProducaoPorTurno()).isEqualByComparingTo("500");
        assertThat(historico.mediaDuracaoMinutos()).isEqualTo(360);
        assertThat(historico.valorMedioNota()).isEqualByComparingTo("200");

        var vazio = DetalharTurnoUseCase.historico(List.of(), List.of());
        assertThat(vazio.turnos()).isZero();
        assertThat(vazio.mediaProducaoPorTurno()).isNull();
    }

    @Test
    @DisplayName("Relatório completo: meta do dia da abertura e histórico dos 30 dias anteriores da equipe")
    void executar() {
        TurnoApi turnoApi = mock(TurnoApi.class);
        ExecucaoComercialApi execucaoApi = mock(ExecucaoComercialApi.class);
        EquipeApi equipeApi = mock(EquipeApi.class);
        ComposicaoDosTurnos composicao = mock(ComposicaoDosTurnos.class);
        ComposicaoDosTurnos.Resolvedor resolvedor = mock(ComposicaoDosTurnos.Resolvedor.class);
        UUID idEquipe = UUID.randomUUID();
        TurnoResponse turno = turno(idEquipe, "2026-10-09T07:00:00-03:00", null, 1);

        when(turnoApi.buscarPorId(turno.id())).thenReturn(turno);
        when(execucaoApi.listarPorTurno(turno.id())).thenReturn(List.of());
        when(execucaoApi.listarLancamentosDoTurno(turno.id()))
                .thenReturn(List.of(lancamento("2026-10-09T08:00:00-03:00", "250", true)));
        when(equipeApi.buscarPorIdResponse(idEquipe))
                .thenReturn(Optional.of(equipe(idEquipe, "EQ-01", new BigDecimal("21000"))));
        when(composicao.para(any())).thenReturn(resolvedor);
        when(resolvedor.composicao(any(), any())).thenReturn(new Composicao("Maria", "ABC1D23", List.of("Maria")));
        when(turnoApi.consultar(any(), any())).thenReturn(new PageImpl<>(List.of()));

        var useCase = new DetalharTurnoUseCase(turnoApi, execucaoApi, equipeApi, composicao,
                MetasMensais.porEquipe(equipeApi::buscarPorIdResponse),
                Clock.fixed(AGORA.toInstant(), CalendarioOperacional.FUSO));
        var relatorio = useCase.executar(turno.id());

        assertThat(relatorio.indicadores().metaDiaria()).isEqualByComparingTo("1000");
        assertThat(relatorio.indicadores().percentualMetaDiaria()).isEqualByComparingTo("25.0");
        assertThat(relatorio.historicoEquipe().turnos()).isZero();

        ArgumentCaptor<ConsultaTurnos> consulta = ArgumentCaptor.forClass(ConsultaTurnos.class);
        verify(turnoApi).consultar(consulta.capture(), any());
        assertThat(consulta.getValue().idsEquipes()).containsExactly(idEquipe);
        assertThat(consulta.getValue().aberturaAte()).isEqualTo(turno.dataInicio());
        assertThat(consulta.getValue().aberturaDe()).isEqualTo(turno.dataInicio().minusDays(30));
    }

    private static LancamentoDoTurno lancamento(String quando, String valor, boolean comLocalizacao) {
        return new LancamentoDoTurno(UUID.randomUUID(), "N-" + quando, OffsetDateTime.parse(quando),
                StatusExecucao.CONCLUIDO, new BigDecimal(valor),
                comLocalizacao ? new BigDecimal("-12.97") : null,
                comLocalizacao ? new BigDecimal("-38.50") : null);
    }
}
