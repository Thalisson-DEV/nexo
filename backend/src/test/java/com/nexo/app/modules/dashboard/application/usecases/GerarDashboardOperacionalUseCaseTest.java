package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalFilter;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse;
import com.nexo.app.modules.dashboard.application.dto.DashboardOperacionalResponse.SituacaoMeta;
import com.nexo.app.modules.dashboard.application.dto.ProducaoPorTipoServico;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TipoServicoApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.LancamentoResumido;
import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.EquipeResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GerarDashboardOperacionalUseCaseTest {

    // Sexta-feira, 09/10/2026, 15h em São Paulo.
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-09T15:00:00-03:00");
    private static final BigDecimal META_21_MIL = new BigDecimal("21000");

    private final EquipesDoRecorte equipesDoRecorte = mock(EquipesDoRecorte.class);
    private final ExecucaoComercialApi execucaoApi = mock(ExecucaoComercialApi.class);
    private final TurnoApi turnoApi = mock(TurnoApi.class);
    private final TipoServicoApi tipoServicoApi = mock(TipoServicoApi.class);
    private GerarDashboardOperacionalUseCase useCase;

    private static final TipoServicoResponse LIGACAO = tipo("CL", "LIGAÇÃO NOVA");
    private static final TipoServicoResponse CORTE = tipo("CS", "CORTE");

    private final UUID idNoRitmo = UUID.randomUUID();
    private final UUID idSemMeta = UUID.randomUUID();
    private final UUID idAtrasada = UUID.randomUUID();
    private final List<LancamentoResumido> lancamentos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        useCase = new GerarDashboardOperacionalUseCase(equipesDoRecorte, execucaoApi, turnoApi,
                new TiposServicoDosLancamentos(tipoServicoApi),
                MetasMensais.das(() -> equipesDoRecorte.listar(null, null)), Clock.fixed(AGORA.toInstant(), CalendarioOperacional.FUSO));
        when(tipoServicoApi.listarTodos()).thenReturn(List.of(LIGACAO, CORTE));
        when(equipesDoRecorte.listar(any(), any())).thenReturn(List.of(
                equipe(idNoRitmo, "EQ-01", META_21_MIL),
                equipe(idSemMeta, "EQ-02", null),
                equipe(idAtrasada, "EQ-03", META_21_MIL)));

        // EQ-01: 6.500 até ontem + 100 hoje. EQ-02: 300. EQ-03: 1.000 (nota antiga, sem tipo).
        lancar(idNoRitmo, "2026-10-01T10:00:00-03:00", "3000", LIGACAO);
        lancar(idNoRitmo, "2026-10-08T10:00:00-03:00", "3500", LIGACAO);
        lancar(idNoRitmo, "2026-10-09T09:00:00-03:00", "100", CORTE);
        lancar(idSemMeta, "2026-10-05T10:00:00-03:00", "300", CORTE);
        lancar(idAtrasada, "2026-10-02T10:00:00-03:00", "1000", null);

        OffsetDateTime inicioOutubro = OffsetDateTime.parse("2026-10-01T00:00:00-03:00");
        when(execucaoApi.listarLancamentosResumidos(eq(inicioOutubro), any(), any())).thenReturn(lancamentos);
        when(execucaoApi.listarLancamentosResumidos(any(), eq(inicioOutubro), any())).thenReturn(List.of(
                new LancamentoResumido(UUID.randomUUID(), idNoRitmo, UUID.randomUUID(), null,
                        OffsetDateTime.parse("2026-09-28T10:00:00-03:00"), new BigDecimal("8000"))));
        when(turnoApi.listarSobrepostosAoPeriodo(any(), any(), any())).thenReturn(List.of(
                turno(idNoRitmo, "2026-10-01T07:00:00-03:00"),
                turno(idNoRitmo, "2026-10-08T07:00:00-03:00"),
                turno(idNoRitmo, "2026-10-09T07:00:00-03:00")));
    }

    @Test
    @DisplayName("Tipos de serviço: notas e produção por tipo, no total e por equipe, com as notas sem tipo por último")
    void tiposDeServico() {
        DashboardOperacionalResponse resposta = useCase.executar(new DashboardOperacionalFilter(null, null, null, null));

        assertThat(resposta.tiposServico())
                .extracting(ProducaoPorTipoServico::descricao, ProducaoPorTipoServico::codigo,
                        ProducaoPorTipoServico::notas, ProducaoPorTipoServico::producao,
                        ProducaoPorTipoServico::percentualNotas)
                .containsExactly(
                        tuple("LIGAÇÃO NOVA", "CL", 2L, new BigDecimal("6500.00"), new BigDecimal("40.0")),
                        tuple("CORTE", "CS", 2L, new BigDecimal("400.00"), new BigDecimal("40.0")),
                        tuple("Não informado", null, 1L, new BigDecimal("1000.00"), new BigDecimal("20.0")));
        assertThat(equipe(resposta, idNoRitmo).tiposServico())
                .extracting(ProducaoPorTipoServico::descricao, ProducaoPorTipoServico::notas)
                .containsExactly(
                        tuple("LIGAÇÃO NOVA", 2L),
                        tuple("CORTE", 1L));
        assertThat(equipe(resposta, idAtrasada).tiposServico().getFirst().idTipoServico()).isNull();
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
        assertThat(resposta.producaoDiaria().get(2).meta()).isEqualByComparingTo("0");
        assertThat(equipe(resposta, idSemMeta).producaoDiaria().get(0).meta()).isNull();

        var noRitmo = equipe(resposta, idNoRitmo);
        // 21.000 × 7/21 dias úteis = 7.000; esperado até ontem: 21.000 × 6/21 = 6.000
        assertThat(noRitmo.metaPeriodo()).isEqualByComparingTo("7000");
        assertThat(noRitmo.metaEsperadaAteAgora()).isEqualByComparingTo("6000");
        assertThat(noRitmo.producao()).isEqualByComparingTo("6600");
        assertThat(noRitmo.percentualMeta()).isEqualByComparingTo("94.3");
        assertThat(noRitmo.situacao()).isEqualTo(SituacaoMeta.NO_RITMO);
        assertThat(noRitmo.notas()).isEqualTo(3);
        assertThat(noRitmo.diasComTurno()).isEqualTo(3);
        assertThat(noRitmo.diasComProducao()).isEqualTo(3);
        assertThat(noRitmo.ultimoLancamento()).isEqualTo(OffsetDateTime.parse("2026-10-09T09:00:00-03:00"));
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
        assertThat(resumo.notas()).isEqualTo(5);
        assertThat(resumo.valorMedioNota()).isEqualByComparingTo("1580");
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

    private void lancar(UUID idEquipe, String quando, String valor, TipoServicoResponse tipo) {
        lancamentos.add(new LancamentoResumido(UUID.randomUUID(), idEquipe, UUID.randomUUID(),
                tipo != null ? tipo.id() : null, OffsetDateTime.parse(quando), new BigDecimal(valor)));
    }

    private static TipoServicoResponse tipo(String codigo, String descricao) {
        return new TipoServicoResponse(UUID.randomUUID(), codigo, descricao, true, null, null);
    }

    private static TurnoResponse turno(UUID idEquipe, String inicio) {
        OffsetDateTime abertura = OffsetDateTime.parse(inicio);
        return new TurnoResponse(UUID.randomUUID(), idEquipe, UUID.randomUUID(), abertura, abertura.plusHours(9),
                StatusTurno.FECHADO, null, Set.of(), false, null);
    }

    static EquipeResponse equipe(UUID id, String codigo, BigDecimal metaMensal) {
        var base = new BaseOperacionalResponse(UUID.randomUUID(), "Base Salvador", "BA", null, null,
                null, null, null, true);
        return new EquipeResponse(id, codigo, null, null, null, null, null, base, null, true, List.of(), metaMensal);
    }
}
