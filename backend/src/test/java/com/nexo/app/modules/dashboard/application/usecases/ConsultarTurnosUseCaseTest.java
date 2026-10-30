package com.nexo.app.modules.dashboard.application.usecases;

import com.nexo.app.modules.dashboard.application.dto.ConsultaTurnosFilter;
import com.nexo.app.modules.dashboard.application.dto.DetalhamentoTurnoResponse.Composicao;
import com.nexo.app.modules.dashboard.domain.CalendarioOperacional;
import com.nexo.app.modules.execucao.ExecucaoComercialApi;
import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.ConsultaTurnos;
import com.nexo.app.modules.execucao.application.dto.ProducaoDoTurno;
import com.nexo.app.modules.execucao.application.dto.TurnoResponse;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.nexo.app.modules.dashboard.application.usecases.GerarDashboardOperacionalUseCaseTest.equipe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsultarTurnosUseCaseTest {

    // Sexta-feira, 09/10/2026, 15h em São Paulo.
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-10-09T15:00:00-03:00");

    private final EquipeApi equipeApi = mock(EquipeApi.class);
    private final TurnoApi turnoApi = mock(TurnoApi.class);
    private final ExecucaoComercialApi execucaoApi = mock(ExecucaoComercialApi.class);
    private final ComposicaoDosTurnos composicaoDosTurnos = mock(ComposicaoDosTurnos.class);
    private final ComposicaoDosTurnos.Resolvedor resolvedor = mock(ComposicaoDosTurnos.Resolvedor.class);
    private ConsultarTurnosUseCase useCase;

    private final UUID idEq01 = UUID.randomUUID();
    private final UUID idEq02 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new ConsultarTurnosUseCase(equipeApi, turnoApi, execucaoApi, composicaoDosTurnos,
                MetasMensais.das(equipeApi::listarEquipesAtivas),
                Clock.fixed(AGORA.toInstant(), CalendarioOperacional.FUSO));
        when(equipeApi.listarEquipesAtivas()).thenReturn(List.of(
                equipe(idEq01, "EQ-01", new BigDecimal("21000")),
                equipe(idEq02, "LV-02", null)));
        when(composicaoDosTurnos.para(any())).thenReturn(resolvedor);
        when(resolvedor.composicao(any(), any())).thenReturn(new Composicao("Maria Souza", "ABC1D23", List.of()));
    }

    private static ConsultaTurnosFilter filtro() {
        return new ConsultaTurnosFilter(null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Linha do turno: duração até agora, produção do turno e percentual da meta do dia da abertura")
    void montaLinhas() {
        TurnoResponse aberto = turno(idEq01, "2026-10-09T07:00:00-03:00", null, 3);
        TurnoResponse esquecido = turno(idEq02, "2026-10-08T19:00:00-03:00", null, 0);
        when(turnoApi.consultar(any(), any())).thenReturn(new PageImpl<>(
                List.of(aberto, esquecido)));
        when(execucaoApi.resumirPorTurnos(any())).thenReturn(List.of(new ProducaoDoTurno(aberto.id(), 2L,
                new BigDecimal("500"), AGORA.minusHours(6), AGORA.minusHours(1))));

        var pagina = useCase.executar(filtro(), PageRequest.of(0, 10));

        var linha = pagina.getContent().getFirst();
        assertThat(linha.codigoEquipe()).isEqualTo("EQ-01");
        assertThat(linha.baseOperacional()).isEqualTo("Base Salvador");
        assertThat(linha.duracaoMinutos()).isEqualTo(8 * 60);
        assertThat(linha.notas()).isEqualTo(2);
        assertThat(linha.producao()).isEqualByComparingTo("500");
        assertThat(linha.valorMedioNota()).isEqualByComparingTo("250");
        // Outubro/2026 tem 21 dias úteis (12/10 é feriado): meta diária de 1.000.
        assertThat(linha.metaDiaria()).isEqualByComparingTo("1000");
        assertThat(linha.percentualMetaDiaria()).isEqualByComparingTo("50.0");
        assertThat(linha.membros()).isEqualTo(3);
        assertThat(linha.placa()).isEqualTo("ABC1D23");
        assertThat(linha.abertoDesdeOutroDia()).isFalse();

        var semNotas = pagina.getContent().get(1);
        assertThat(semNotas.notas()).isZero();
        assertThat(semNotas.producao()).isEqualByComparingTo("0");
        assertThat(semNotas.metaDiaria()).isNull();
        assertThat(semNotas.abertoDesdeOutroDia()).isTrue();
    }

    @Test
    @DisplayName("Filtros de dia viram instantes de São Paulo, com o 'até' cobrindo o dia inteiro")
    void traduzFiltros() {
        when(turnoApi.consultar(any(), any())).thenReturn(Page.empty());

        useCase.executar(new ConsultaTurnosFilter(idEq01, null, null, StatusTurno.FECHADO,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9), true, null),
                PageRequest.of(0, 10));

        ArgumentCaptor<ConsultaTurnos> consulta = ArgumentCaptor.forClass(ConsultaTurnos.class);
        verify(turnoApi).consultar(consulta.capture(), any());
        assertThat(consulta.getValue().idsEquipes()).containsExactly(idEq01);
        assertThat(consulta.getValue().status()).isEqualTo(StatusTurno.FECHADO);
        assertThat(consulta.getValue().aberturaDe()).isEqualTo(OffsetDateTime.parse("2026-10-01T00:00:00-03:00"));
        assertThat(consulta.getValue().aberturaAte()).isEqualTo(OffsetDateTime.parse("2026-10-10T00:00:00-03:00"));
        assertThat(consulta.getValue().ativoDe()).isEqualTo(OffsetDateTime.parse("2026-10-09T00:00:00-03:00"));
        assertThat(consulta.getValue().ativoAte()).isEqualTo(OffsetDateTime.parse("2026-10-10T00:00:00-03:00"));
    }

    @Test
    @DisplayName("Busca rápida, base e processo restringem as equipes; nenhuma combinação vira lista vazia")
    void equipesAceitas() {
        var processo = new ProcessoNegocioResponse(UUID.randomUUID(), "Corte", true);
        var comProcesso = new EquipeResponse(idEq02, "LV-02", null, processo, null, null, null, null, null, true,
                List.of(), null);
        List<EquipeResponse> equipes = List.of(equipe(idEq01, "EQ-01", null), comProcesso);

        assertThat(ConsultarTurnosUseCase.equipesAceitas(filtro(), equipes)).isNull();
        assertThat(ConsultarTurnosUseCase.equipesAceitas(
                new ConsultaTurnosFilter(null, null, null, null, null, null, null, " lv "), equipes))
                .containsExactly(idEq02);
        assertThat(ConsultarTurnosUseCase.equipesAceitas(
                new ConsultaTurnosFilter(null, null, List.of(processo.id()), null, null, null, null, null), equipes))
                .containsExactly(idEq02);
        assertThat(ConsultarTurnosUseCase.equipesAceitas(
                new ConsultaTurnosFilter(null, null, List.of(processo.id(), UUID.randomUUID()), null, null, null, null, null), equipes))
                .containsExactly(idEq02);
        assertThat(ConsultarTurnosUseCase.equipesAceitas(
                new ConsultaTurnosFilter(idEq01, null, List.of(processo.id()), null, null, null, null, null), equipes))
                .isEmpty();
    }

    @Test
    @DisplayName("Ordenação por coluna calculada é descartada e cai na abertura mais recente")
    void ordenacaoSegura() {
        var pedido = PageRequest.of(2, 25,
                Sort.by("producao", "dataFim"));
        var seguro = ConsultarTurnosUseCase.ordenacaoSegura(pedido);
        assertThat(seguro.getPageNumber()).isEqualTo(2);
        assertThat(seguro.getSort().stream().map(o -> o.getProperty()).toList()).containsExactly("dataFim");

        var semValidas = ConsultarTurnosUseCase.ordenacaoSegura(PageRequest.of(0, 10,
                Sort.by("equipe.codigo")));
        assertThat(semValidas.getSort().getOrderFor("dataInicio")).isNotNull();
        assertThat(semValidas.getSort().getOrderFor("dataInicio").isDescending()).isTrue();
    }

    @Test
    @DisplayName("Equipe desativada que aparece num turno antigo é buscada uma única vez")
    void equipeDesativada() {
        UUID idAntiga = UUID.randomUUID();
        when(turnoApi.consultar(any(), any())).thenReturn(new PageImpl<>(List.of(
                turno(idAntiga, "2026-09-01T07:00:00-03:00", "2026-09-01T16:00:00-03:00", 0),
                turno(idAntiga, "2026-09-02T07:00:00-03:00", "2026-09-02T16:00:00-03:00", 0))));
        when(execucaoApi.resumirPorTurnos(any())).thenReturn(List.of());
        when(equipeApi.buscarPorIdResponse(idAntiga)).thenReturn(Optional.of(equipe(idAntiga, "EQ-OLD", null)));

        var pagina = useCase.executar(filtro(), PageRequest.of(0, 10));

        assertThat(pagina.getContent()).extracting("codigoEquipe").containsOnly("EQ-OLD");
        verify(equipeApi, times(1)).buscarPorIdResponse(idAntiga);
    }

    static TurnoResponse turno(UUID idEquipe, String inicio, String fim, int membros) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (int i = 0; i < membros; i++) {
            ids.add(UUID.randomUUID());
        }
        return new TurnoResponse(UUID.randomUUID(), idEquipe, UUID.randomUUID(), OffsetDateTime.parse(inicio),
                fim != null ? OffsetDateTime.parse(fim) : null,
                fim != null ? StatusTurno.FECHADO : StatusTurno.ABERTO, null, ids, false, null);
    }
}
