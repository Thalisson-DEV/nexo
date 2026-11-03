package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.ProducaoPorObra;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.SituacaoMeta;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.obras.ExecucaoObrasApi;
import com.nexo.app.modules.obras.ExecucaoObrasApi.ExecutadoNoDia;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GerarDashboardOperacionalUseCaseTest {

    // Sexta-feira, 09/10/2026, 15h em São Paulo.
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-09T15:00:00-03:00");
    private static final BigDecimal META_21_MIL = new BigDecimal("21000");
    private static final ProcessoNegocioResponse OBRAS = new ProcessoNegocioResponse(UUID.randomUUID(), "OBRAS", true);
    private static final ProcessoNegocioResponse COMERCIAL = new ProcessoNegocioResponse(UUID.randomUUID(), "COMERCIAL", true);

    private final EquipesDoRecorte equipesDoRecorte = mock(EquipesDoRecorte.class);
    private final ExecucaoObrasApi execucaoObrasApi = mock(ExecucaoObrasApi.class);
    private final TurnoApi turnoApi = mock(TurnoApi.class);
    private GerarDashboardOperacionalUseCase useCase;

    private final UUID idNoRitmo = UUID.randomUUID();
    private final UUID idSemMeta = UUID.randomUUID();
    private final UUID idAtrasada = UUID.randomUUID();
    private final UUID idComercial = UUID.randomUUID();
    private final UUID obraA = UUID.randomUUID();
    private final UUID obraB = UUID.randomUUID();
    private final UUID obraC = UUID.randomUUID();
    private final List<ExecutadoNoDia> executado = new ArrayList<>();

    @BeforeEach
    void setUp() {
        useCase = new GerarDashboardOperacionalUseCase(equipesDoRecorte, execucaoObrasApi, turnoApi,
                MetasMensais.das(() -> equipesDoRecorte.listar(null, null)), Clock.fixed(AGORA.toInstant(), CalendarioOperacional.FUSO));
        when(execucaoObrasApi.processosComObras()).thenReturn(Set.of(OBRAS.id()));
        when(equipesDoRecorte.listar(any(), any())).thenReturn(List.of(
                equipe(idNoRitmo, "EQ-01", META_21_MIL),
                equipe(idSemMeta, "EQ-02", null),
                equipe(idAtrasada, "EQ-03", META_21_MIL),
                equipe(idComercial, "CM-01", META_21_MIL, COMERCIAL)));

        // EQ-01: 6.500 até ontem (obra A) + 100 hoje (obra B). EQ-02: 300 (obra B). EQ-03: 1.000 (obra C).
        executar(idNoRitmo, obraA, "OB-A", "2026-10-01", "3000", 2);
        executar(idNoRitmo, obraA, "OB-A", "2026-10-08", "3500", 3);
        executar(idNoRitmo, obraB, "OB-B", "2026-10-09", "100", 1);
        executar(idSemMeta, obraB, "OB-B", "2026-10-05", "300", 1);
        executar(idAtrasada, obraC, "OB-C", "2026-10-02", "1000", 1);

        when(execucaoObrasApi.executadoPorDia(anyCollection(), eq(LocalDate.of(2026, 10, 1)), any())).thenReturn(executado);
        when(execucaoObrasApi.executadoPorDia(anyCollection(), any(), eq(LocalDate.of(2026, 9, 30)))).thenReturn(List.of(
                new ExecutadoNoDia(obraA, "OB-A", "Obra A", idNoRitmo, LocalDate.of(2026, 9, 28), new BigDecimal("8000"), 4)));
        when(turnoApi.listarSobrepostosAoPeriodo(any(), any(), any())).thenReturn(List.of(
                turno(idNoRitmo, "2026-10-01T07:00:00-03:00"),
                turno(idNoRitmo, "2026-10-08T07:00:00-03:00"),
                turno(idNoRitmo, "2026-10-09T07:00:00-03:00")));
    }

    @Test
    @DisplayName("Só entram as equipes dos processos que têm obras")
    void soEquipesDeObras() {
        DashboardOperacionalResponse resposta = useCase.executar(new DashboardOperacionalFilter(null, null, null, null));

        assertThat(resposta.equipes()).extracting(DashboardOperacionalResponse.Equipe::codigo)
                .containsExactly("EQ-01", "EQ-02", "EQ-03");
    }

    @Test
    @DisplayName("Obras do período: produção, equipes e serviços por obra, no total e por equipe, da maior para a menor")
    void obrasDoPeriodo() {
        DashboardOperacionalResponse resposta = useCase.executar(new DashboardOperacionalFilter(null, null, null, null));

        assertThat(resposta.obras())
                .extracting(ProducaoPorObra::numero, ProducaoPorObra::equipes, ProducaoPorObra::servicos,
                        ProducaoPorObra::diasComExecucao, ProducaoPorObra::producao, ProducaoPorObra::percentualProducao)
                .containsExactly(
                        tuple("OB-A", 1, 5L, 2L, new BigDecimal("6500.00"), new BigDecimal("82.3")),
                        tuple("OB-C", 1, 1L, 1L, new BigDecimal("1000.00"), new BigDecimal("12.7")),
                        tuple("OB-B", 2, 2L, 2L, new BigDecimal("400.00"), new BigDecimal("5.1")));
        assertThat(equipe(resposta, idNoRitmo).obras())
                .extracting(ProducaoPorObra::numero, ProducaoPorObra::producao)
                .containsExactly(
                        tuple("OB-A", new BigDecimal("6500.00")),
                        tuple("OB-B", new BigDecimal("100.00")));
        assertThat(resposta.obras().getFirst().ultimoApontamento()).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    @DisplayName("Sem datas, o recorte é o mês corrente até hoje, e a meta é proporcional aos dias úteis")
    void recortePadrao() {
        DashboardOperacionalResponse resposta = useCase.executar(new DashboardOperacionalFilter(null, null, null, null));

        assertThat(resposta.inicio()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(resposta.fim()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(resposta.diasUteisPeriodo()).isEqualTo(7);
        assertThat(resposta.diasUteisEncerrados()).isEqualTo(6);
        assertThat(resposta.producaoDiaria()).hasSize(9);
        // Meta do dia: 21.000/21 = 1.000 por equipe com meta (duas) em dia útil; zero no fim de semana.
        assertThat(resposta.producaoDiaria().get(0).meta()).isEqualByComparingTo("2000");
        assertThat(resposta.producaoDiaria().get(0).obras()).isEqualTo(1);
        assertThat(resposta.producaoDiaria().get(0).servicos()).isEqualTo(2);
        assertThat(resposta.producaoDiaria().get(2).meta()).isEqualByComparingTo("0");
        assertThat(equipe(resposta, idSemMeta).producaoDiaria().get(0).meta()).isNull();

        var noRitmo = equipe(resposta, idNoRitmo);
        // 21.000 × 7/21 dias úteis = 7.000; esperado até ontem: 21.000 × 6/21 = 6.000
        assertThat(noRitmo.metaPeriodo()).isEqualByComparingTo("7000");
        assertThat(noRitmo.metaEsperadaAteAgora()).isEqualByComparingTo("6000");
        assertThat(noRitmo.producao()).isEqualByComparingTo("6600");
        assertThat(noRitmo.percentualMeta()).isEqualByComparingTo("94.3");
        assertThat(noRitmo.situacao()).isEqualTo(SituacaoMeta.NO_RITMO);
        assertThat(noRitmo.obrasAtendidas()).isEqualTo(2);
        assertThat(noRitmo.valorMedioObra()).isEqualByComparingTo("3300");
        assertThat(noRitmo.servicos()).isEqualTo(6);
        assertThat(noRitmo.diasComTurno()).isEqualTo(3);
        assertThat(noRitmo.diasComProducao()).isEqualTo(3);
        assertThat(noRitmo.ultimoApontamento()).isEqualTo(LocalDate.of(2026, 10, 9));
        // Período termina hoje: não há dias úteis futuros, a projeção é a própria produção.
        assertThat(noRitmo.projecao()).isEqualByComparingTo("6600");

        assertThat(equipe(resposta, idSemMeta).situacao()).isEqualTo(SituacaoMeta.SEM_META);
        assertThat(equipe(resposta, idSemMeta).metaPeriodo()).isNull();
        assertThat(equipe(resposta, idAtrasada).situacao()).isEqualTo(SituacaoMeta.ABAIXO_DO_RITMO);
    }

    @Test
    @DisplayName("O resumo compara a produção das equipes com meta à soma das metas e ao período anterior")
    void resumo() {
        var resumo = useCase.executar(new DashboardOperacionalFilter(null, null, null, null)).resumo();

        assertThat(resumo.producao()).isEqualByComparingTo("7900");
        assertThat(resumo.obrasAtendidas()).isEqualTo(3);
        assertThat(resumo.valorMedioObra()).isEqualByComparingTo("2633.33");
        assertThat(resumo.servicos()).isEqualTo(8);
        assertThat(resumo.equipesAtivas()).isEqualTo(3);
        assertThat(resumo.equipesComProducao()).isEqualTo(3);
        assertThat(resumo.equipesComMeta()).isEqualTo(2);
        assertThat(resumo.metaPeriodo()).isEqualByComparingTo("14000");
        assertThat(resumo.producaoEquipesComMeta()).isEqualByComparingTo("7600");
        assertThat(resumo.percentualMeta()).isEqualByComparingTo("54.3");
        assertThat(resumo.equipesAbaixoDoRitmo()).isEqualTo(1);
        assertThat(resumo.producaoPeriodoAnterior()).isEqualByComparingTo("8000");
        assertThat(resumo.variacaoPercentual()).isEqualByComparingTo("-1.3");
    }

    @Test
    @DisplayName("Com dias úteis à frente, projeta mantendo o ritmo dos dias úteis encerrados")
    void projecaoComDiasFuturos() {
        var resposta = useCase.executar(new DashboardOperacionalFilter(null, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        var noRitmo = equipe(resposta, idNoRitmo);
        // Meta do mês inteiro; ritmo = 6.500 / 6 dias úteis; faltam 14 dias úteis (13 a 30/10; 12/10 é feriado).
        assertThat(noRitmo.metaPeriodo()).isEqualByComparingTo("21000");
        assertThat(noRitmo.projecao()).isEqualByComparingTo("21766.67");
        assertThat(resposta.diasUteisPeriodo()).isEqualTo(21);
    }

    @Test
    @DisplayName("Recusa período invertido ou maior que um ano")
    void validaPeriodo() {
        assertThatThrownBy(() -> useCase.executar(new DashboardOperacionalFilter(null, null,
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 1))))
                .isInstanceOf(InvarianteVioladaException.class);
        assertThatThrownBy(() -> useCase.executar(new DashboardOperacionalFilter(null, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 1))))
                .isInstanceOf(InvarianteVioladaException.class);
    }

    private DashboardOperacionalResponse.Equipe equipe(DashboardOperacionalResponse resposta, UUID id) {
        return resposta.equipes().stream().filter(e -> e.idEquipe().equals(id)).findFirst().orElseThrow();
    }

    private void executar(UUID idEquipe, UUID idObra, String numero, String dia, String valor, long servicos) {
        executado.add(new ExecutadoNoDia(idObra, numero, "Obra " + numero, idEquipe, LocalDate.parse(dia),
                new BigDecimal(valor), servicos));
    }

    private static TurnoResponse turno(UUID idEquipe, String inicio) {
        OffsetDateTime abertura = OffsetDateTime.parse(inicio);
        return new TurnoResponse(UUID.randomUUID(), idEquipe, UUID.randomUUID(), abertura, abertura.plusHours(9),
                StatusTurno.FECHADO, null, Set.of(), false, null);
    }

    static EquipeResponse equipe(UUID id, String codigo, BigDecimal metaMensal) {
        return equipe(id, codigo, metaMensal, OBRAS);
    }

    static EquipeResponse equipe(UUID id, String codigo, BigDecimal metaMensal, ProcessoNegocioResponse processo) {
        var base = new BaseOperacionalResponse(UUID.randomUUID(), "Base Salvador", "BA", null, null,
                null, null, null, true);
        return new EquipeResponse(id, codigo, null, processo, null, null, null, base, null, true, List.of(), metaMensal);
    }
}
