package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse;
import com.nexo.app.modules.dashboard.application.dto.DashboardTurnosResponse.SituacaoTurno;
import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Composicao;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TipoServicoApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCaseTest.equipe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GerarDashboardTurnosUseCaseTest {

    // Sexta-feira, 09/10/2026, 15h em São Paulo.
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-09T15:00:00-03:00");

    private final EquipesDoRecorte equipesDoRecorte = mock(EquipesDoRecorte.class);
    private final ExecucaoComercialApi execucaoApi = mock(ExecucaoComercialApi.class);
    private final TurnoApi turnoApi = mock(TurnoApi.class);
    private final ComposicaoDosTurnos composicaoDosTurnos = mock(ComposicaoDosTurnos.class);
    private final ComposicaoDosTurnos.Resolvedor resolvedor = mock(ComposicaoDosTurnos.Resolvedor.class);
    private GerarDashboardTurnosUseCase useCase;

    private final UUID idEmTurno = UUID.randomUUID();
    private final UUID idEncerrada = UUID.randomUUID();
    private final UUID idSemTurno = UUID.randomUUID();
    private final UUID idEsquecida = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TipoServicoApi tipoServicoApi = mock(TipoServicoApi.class);
        when(tipoServicoApi.listarTodos()).thenReturn(List.of());
        useCase = new GerarDashboardTurnosUseCase(equipesDoRecorte, execucaoApi, turnoApi, composicaoDosTurnos,
                new TiposServicoDosLancamentos(tipoServicoApi),
                MetasMensais.das(() -> equipesDoRecorte.listar(null, null)), Clock.fixed(AGORA.toInstant(), CalendarioOperacional.FUSO));
        when(equipesDoRecorte.listar(any(), any())).thenReturn(List.of(
                equipe(idEmTurno, "EQ-01", new BigDecimal("21000")),
                equipe(idEncerrada, "EQ-02", null),
                equipe(idSemTurno, "EQ-03", null),
                equipe(idEsquecida, "EQ-04", null)));
        when(turnoApi.listarSobrepostosAoPeriodo(any(), any(), any())).thenReturn(List.of(
                turno(idEmTurno, "2026-10-09T07:00:00-03:00", null),
                turno(idEncerrada, "2026-10-09T07:00:00-03:00", "2026-10-09T11:00:00-03:00"),
                turno(idEsquecida, "2026-10-08T20:00:00-03:00", null)));
        when(execucaoApi.listarLancamentosResumidos(any(), any(), any())).thenReturn(List.of(
                lancamento(idEmTurno, "2026-10-09T08:00:00-03:00", "100"),
                lancamento(idEmTurno, "2026-10-09T12:30:00-03:00", "200"),
                lancamento(idEncerrada, "2026-10-09T09:00:00-03:00", "50")));
        when(composicaoDosTurnos.para(any())).thenReturn(resolvedor);
        when(resolvedor.composicao(any(), any()))
                .thenReturn(new Composicao("Maria Souza", "ABC1D23", List.of("João Lima", "Maria Souza")));
    }

    @Test
    @DisplayName("Equipe em turno: produção do dia contra a meta diária e alerta de tempo sem lançar")
    void equipeEmTurno() {
        var resposta = useCase.executar(new DashboardTurnosFilter(null, null, null));

        assertThat(resposta.data()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(resposta.hoje()).isTrue();
        assertThat(resposta.diaUtil()).isTrue();

        var emTurno = daEquipe(resposta, idEmTurno);
        assertThat(emTurno.situacao()).isEqualTo(SituacaoTurno.EM_TURNO);
        assertThat(emTurno.minutosEmTurno()).isEqualTo(480);
        assertThat(emTurno.notas()).isEqualTo(2);
        assertThat(emTurno.producao()).isEqualByComparingTo("300");
        assertThat(emTurno.metaDiaria()).isEqualByComparingTo("1000");
        assertThat(emTurno.percentualMetaDiaria()).isEqualByComparingTo("30.0");
        // Último lançamento às 12h30, agora são 15h.
        assertThat(emTurno.minutosSemLancamento()).isEqualTo(150);
        assertThat(emTurno.semLancamentoRecente()).isTrue();
        assertThat(emTurno.lancamentos()).hasSize(2);
        assertThat(emTurno.placa()).isEqualTo("ABC1D23");
        assertThat(emTurno.membros()).containsExactly("João Lima", "Maria Souza");
    }

    @Test
    @DisplayName("Distingue turno encerrado, equipe sem turno e turno esquecido aberto desde outro dia")
    void situacoes() {
        var resposta = useCase.executar(new DashboardTurnosFilter(null, null, null));

        var encerrada = daEquipe(resposta, idEncerrada);
        assertThat(encerrada.situacao()).isEqualTo(SituacaoTurno.ENCERRADO);
        assertThat(encerrada.minutosEmTurno()).isEqualTo(240);
        assertThat(encerrada.fechamento()).isEqualTo(OffsetDateTime.parse("2026-10-09T11:00:00-03:00"));
        assertThat(encerrada.minutosSemLancamento()).isNull();
        assertThat(encerrada.metaDiaria()).isNull();

        var semTurno = daEquipe(resposta, idSemTurno);
        assertThat(semTurno.situacao()).isEqualTo(SituacaoTurno.SEM_TURNO);
        assertThat(semTurno.idTurno()).isNull();

        var esquecida = daEquipe(resposta, idEsquecida);
        assertThat(esquecida.situacao()).isEqualTo(SituacaoTurno.EM_TURNO);
        assertThat(esquecida.abertoDesdeOutroDia()).isTrue();
        // Conta só a parte de hoje: da meia-noite às 15h.
        assertThat(esquecida.minutosEmTurno()).isEqualTo(900);

        var resumo = resposta.resumo();
        assertThat(resumo.equipesAtivas()).isEqualTo(4);
        assertThat(resumo.emTurno()).isEqualTo(2);
        assertThat(resumo.encerrados()).isEqualTo(1);
        assertThat(resumo.semTurno()).isEqualTo(1);
        assertThat(resumo.semLancamentoRecente()).isEqualTo(2);
        assertThat(resumo.turnosAbertosDeOutroDia()).isEqualTo(1);
        assertThat(resumo.notas()).isEqualTo(3);
        assertThat(resumo.producao()).isEqualByComparingTo("350");
        assertThat(resumo.metaDiaria()).isEqualByComparingTo("1000");
        assertThat(resumo.percentualMetaDiaria()).isEqualByComparingTo("30.0");
    }

    @Test
    @DisplayName("Em um dia passado não há alerta de tempo sem lançamento")
    void diaPassado() {
        var resposta = useCase.executar(new DashboardTurnosFilter(null, null, LocalDate.of(2026, 10, 8)));

        assertThat(resposta.hoje()).isFalse();
        assertThat(resposta.equipes()).allSatisfy(e -> {
            assertThat(e.minutosSemLancamento()).isNull();
            assertThat(e.semLancamentoRecente()).isFalse();
        });
    }

    private DashboardTurnosResponse.Equipe daEquipe(DashboardTurnosResponse resposta, UUID id) {
        return resposta.equipes().stream().filter(e -> e.idEquipe().equals(id)).findFirst().orElseThrow();
    }

    private static TurnoResponse turno(UUID idEquipe, String inicio, String fim) {
        return new TurnoResponse(UUID.randomUUID(), idEquipe, UUID.randomUUID(), OffsetDateTime.parse(inicio),
                fim != null ? OffsetDateTime.parse(fim) : null,
                fim != null ? StatusTurno.FECHADO : StatusTurno.ABERTO, null, Set.of(), false, null);
    }

    private static LancamentoResumido lancamento(UUID idEquipe, String quando, String valor) {
        return new LancamentoResumido(UUID.randomUUID(), idEquipe, UUID.randomUUID(), null,
                OffsetDateTime.parse(quando), new BigDecimal(valor));
    }
}
