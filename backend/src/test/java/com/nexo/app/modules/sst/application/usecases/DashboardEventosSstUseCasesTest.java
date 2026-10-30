package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstFilter;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstResponse;
import com.nexo.app.modules.sst.application.dto.DiasSemAcidenteItem;
import com.nexo.app.modules.sst.application.dto.FaixaBacklogEventos;
import com.nexo.app.modules.sst.application.dto.PiramideAcidentalidadeItem;
import com.nexo.app.modules.sst.domain.DadosEventoSst;
import com.nexo.app.modules.sst.domain.EventoSst;
import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import com.nexo.app.modules.sst.domain.PapelEnvolvido;
import com.nexo.app.modules.sst.domain.RegistroEvidencia;
import com.nexo.app.modules.sst.domain.SnapshotAtividade;
import com.nexo.app.modules.sst.domain.SnapshotEnvolvido;
import com.nexo.app.modules.sst.domain.SnapshotOperacional;
import com.nexo.app.modules.sst.domain.SnapshotTecnico;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoCat;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.EventoSstRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Indicadores de acidentalidade: o que entra na conta, o que é excluído e o que
 * acontece quando não há evento nenhum.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardEventosSstUseCasesTest {

    private static final UUID ID_ATIVIDADE = UUID.randomUUID();
    private static final UUID ID_GESTOR = UUID.randomUUID();
    private static final UUID ID_BASE = UUID.randomUUID();
    private static final UUID ID_BASE_LIMPA = UUID.randomUUID();
    private static final UUID ID_CLASSIFICACAO = UUID.randomUUID();
    private static final UUID ID_LOCAL_LESAO = UUID.randomUUID();

    @Mock
    private EventoSstRepository repository;
    @Mock
    private CatalogoSstRepository catalogoRepository;
    @Mock
    private DesvioSegurancaRepository desvioRepository;
    @Mock
    private ProcessoNegocioApi processoApi;

    @InjectMocks
    private DashboardEventosSstUseCases useCases;

    @BeforeEach
    void configurarPadroes() {
        when(catalogoRepository.findAllById(anyCollection())).thenReturn(List.of());
        when(desvioRepository.count(any(Specification.class))).thenReturn(0L);
        when(repository.buscarUltimaDataOcorrenciaValida(StatusEventoSst.CANCELADO))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Deve consolidar os indicadores do período e comparar com o anterior")
    void deveConsolidarIndicadores() {
        responderCom(
                List.of(acidente(3), acidente(2), incidenteSemLesao(), incidenteSemLesao(), incidenteSemLesao()),
                List.of(acidente(1)));

        DashboardEventosSstResponse dashboard = useCases.gerar(filtro());

        assertEquals(5, dashboard.resumo().total());
        assertEquals(2, dashboard.resumo().comLesao());
        assertEquals(3, dashboard.resumo().semLesao());
        assertEquals(5, dashboard.resumo().diasPerdidos());
        assertEquals(1, dashboard.resumo().totalAnterior());
    }

    @Test
    @DisplayName("A base da pirâmide vem dos desvios apontados em inspeção, não de um tipo de evento")
    void baseDaPiramideVemDosDesvios() {
        responderCom(List.of(acidente(0), acidente(0)), List.of());
        when(desvioRepository.count(any(Specification.class))).thenReturn(12L);

        DashboardEventosSstResponse dashboard = useCases.gerar(filtro());

        PiramideAcidentalidadeItem base = dashboard.piramide().getLast();
        assertEquals(PiramideAcidentalidadeItem.CHAVE_DESVIO, base.chave());
        assertEquals(12, base.total());
        // Doze desvios para dois eventos com lesão.
        assertEquals(0, base.razaoPorEventoComLesao().compareTo(new java.math.BigDecimal("6.00")));
        assertEquals(12, dashboard.resumo().desviosNoPeriodo());
        assertEquals(0, dashboard.resumo().razaoDesviosPorLesao().compareTo(new java.math.BigDecimal("6.00")));
    }

    @Test
    @DisplayName("A pirâmide traz os cinco níveis de severidade, incluindo os desvios")
    void piramideTemCincoDegraus() {
        responderCom(List.of(acidente(0)), List.of());

        assertEquals(5, useCases.gerar(filtro()).piramide().size());
    }

    @Test
    @DisplayName("Deve excluir eventos cancelados de todos os indicadores")
    void deveExcluirCancelados() {
        EventoSst cancelado = acidente(10);
        cancelado.cancelar("Registro duplicado");
        responderCom(List.of(acidente(2), cancelado), List.of());

        DashboardEventosSstResponse dashboard = useCases.gerar(filtro());

        assertEquals(1, dashboard.resumo().total());
        assertEquals(2, dashboard.resumo().diasPerdidos(), "os dias perdidos do cancelado não entram");
    }

    @Test
    @DisplayName("Período sem evento deve devolver indicadores zerados, e não erro")
    void devePermitirPeriodoVazio() {
        responderCom(List.of(), List.of());

        DashboardEventosSstResponse dashboard = useCases.gerar(filtro());

        assertEquals(0, dashboard.resumo().total());
        assertNull(dashboard.resumo().diasSemEventos());
        assertEquals(0, dashboard.resumo().mediaHorasComunicacao().signum());
        assertTrue(dashboard.porClassificacao().isEmpty());
        assertTrue(dashboard.diasSemAcidente().isEmpty());
        assertNotNull(dashboard.piramide());
        assertEquals(5, dashboard.piramide().size(), "os cinco níveis devem existir mesmo zerados");
    }

    @Test
    @DisplayName("Dias sem eventos deve considerar todo o histórico e ignorar os filtros do dashboard")
    void deveCalcularDiasSemEventosGlobalmente() {
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        when(repository.buscarUltimaDataOcorrenciaValida(StatusEventoSst.CANCELADO))
                .thenReturn(Optional.of(OffsetDateTime.now(fuso).minusDays(18)));
        responderCom(List.of(), List.of());

        DashboardEventosSstResponse dashboard = useCases.gerar(filtro(
                OffsetDateTime.now().minusDays(3), OffsetDateTime.now(), GranularidadeDashboard.DIA));

        assertEquals(18L, dashboard.resumo().diasSemEventos());
        verify(repository).buscarUltimaDataOcorrenciaValida(StatusEventoSst.CANCELADO);
    }

    @Test
    @DisplayName("Deve escolher a granularidade pelo tamanho do período e respeitar a explícita")
    void deveEscolherGranularidade() {
        responderCom(List.of(), List.of());

        DashboardEventosSstResponse curto = useCases.gerar(filtro(
                OffsetDateTime.now().minusDays(10), OffsetDateTime.now(), null));
        assertEquals(GranularidadeDashboard.DIA, curto.granularidade());

        DashboardEventosSstResponse longo = useCases.gerar(filtro(
                OffsetDateTime.now().minusDays(400), OffsetDateTime.now(), null));
        assertEquals(GranularidadeDashboard.MES, longo.granularidade());

        DashboardEventosSstResponse explicito = useCases.gerar(filtro(
                OffsetDateTime.now().minusDays(10), OffsetDateTime.now(), GranularidadeDashboard.ANO));
        assertEquals(GranularidadeDashboard.ANO, explicito.granularidade());
    }

    @Test
    @DisplayName("A série deve trazer todos os baldes do período, inclusive os vazios")
    void deveTrazerBaldesVazios() {
        responderCom(List.of(acidente(1)), List.of());

        DashboardEventosSstResponse dashboard = useCases.gerar(filtro(
                OffsetDateTime.now().minusDays(4), OffsetDateTime.now(), GranularidadeDashboard.DIA));

        assertEquals(5, dashboard.serie().size());
        assertTrue(dashboard.serie().stream().anyMatch(item -> item.total() == 0));
    }

    @Test
    @DisplayName("Dias sem acidente deve olhar a série inteira e marcar base sem ocorrência")
    void deveCalcularDiasSemAcidente() {
        EventoSst antigoComLesao = eventoEm(TipoEventoSst.ACIDENTE_TIPICO, ID_BASE,
                OffsetDateTime.now().minusDays(30));

        responderCom(
                List.of(acidente(1), incidenteNaBase(ID_BASE_LIMPA)),
                List.of(),
                List.of(antigoComLesao),
                List.of());

        List<DiasSemAcidenteItem> itens = useCases.gerar(filtro()).diasSemAcidente();

        assertEquals(2, itens.size());
        DiasSemAcidenteItem central = itens.stream()
                .filter(item -> item.idBaseOperacional().equals(ID_BASE)).findFirst().orElseThrow();
        DiasSemAcidenteItem limpa = itens.stream()
                .filter(item -> item.idBaseOperacional().equals(ID_BASE_LIMPA)).findFirst().orElseThrow();

        assertEquals(30, central.diasSemAcidente());
        assertNull(limpa.diasSemAcidente(), "base sem evento com lesão não tem contagem");
        assertNull(limpa.ultimaOcorrencia());
    }

    @Test
    @DisplayName("A pirâmide agrega os eventos por consequência")
    void deveMontarPiramide() {
        responderCom(List.of(fatalidade(), acidente(3), acidente(0),
                        incidenteSemLesao(), incidenteSemLesao(), incidenteSemLesao(), incidenteSemLesao()),
                List.of());

        List<PiramideAcidentalidadeItem> piramide = useCases.gerar(filtro()).piramide();
        PiramideAcidentalidadeItem incidentes = nivel(piramide, PiramideAcidentalidadeItem.CHAVE_INCIDENTE);

        assertEquals(1, nivel(piramide, PiramideAcidentalidadeItem.CHAVE_FATALIDADE).total());
        assertEquals(1, nivel(piramide, PiramideAcidentalidadeItem.CHAVE_ACIDENTE_COM_AFASTAMENTO).total());
        assertEquals(1, nivel(piramide, PiramideAcidentalidadeItem.CHAVE_ACIDENTE_SEM_AFASTAMENTO).total());
        assertEquals(4, incidentes.total());
        assertEquals(0, incidentes.razaoPorEventoComLesao().compareTo(new java.math.BigDecimal("1.33")));
    }

    @Test
    @DisplayName("Backlog deve considerar só os eventos abertos, pela idade da ocorrência")
    void deveMontarBacklog() {
        EventoSst concluido = acidente(0);
        concluido.concluir("Tratado");
        responderCom(List.of(acidente(0), concluido), List.of());

        long abertosNoBacklog = useCases.gerar(filtro()).backlog().stream()
                .mapToLong(faixa -> faixa.total()).sum();

        assertEquals(1, abertosNoBacklog);
    }

    @Test
    @DisplayName("Backlog deve mostrar evento antigo ainda em aberto, mesmo fora do período consultado")
    void backlogDeveIgnorarOPeriodo() {
        EventoSst antigoEmAberto = eventoEm(TipoEventoSst.ACIDENTE_TIPICO, ID_BASE,
                OffsetDateTime.now().minusDays(45));

        // O período traz só um evento recente; o antigo entra apenas pela consulta de abertos.
        responderCom(List.of(incidenteSemLesao()), List.of(), List.of(), List.of(antigoEmAberto));

        List<FaixaBacklogEventos> backlog = useCases.gerar(filtro(
                OffsetDateTime.now().minusDays(7), OffsetDateTime.now(), null)).backlog();

        FaixaBacklogEventos maisDe30 = backlog.stream()
                .filter(faixa -> faixa.idadeMaximaDias() == null).findFirst().orElseThrow();

        assertEquals(1, maisDe30.total(), "um acidente de 45 dias em aberto pertence à última faixa");
        assertEquals(1, backlog.stream().mapToLong(FaixaBacklogEventos::total).sum());
    }

    @Test
    @DisplayName("Deve recusar período com fim anterior ao início")
    void deveRecusarPeriodoInvertido() {
        assertThrows(InvarianteVioladaException.class, () -> useCases.gerar(filtro(
                OffsetDateTime.now(), OffsetDateTime.now().minusDays(5), null)));
    }

    @Test
    @DisplayName("Sem filtro nenhum, deve assumir o mês corrente sem quebrar")
    void deveAssumirMesCorrente() {
        responderCom(List.of(), List.of());

        DashboardEventosSstResponse dashboard = useCases.gerar(null);

        assertEquals(1, dashboard.inicio().getDayOfMonth());
        assertFalse(dashboard.fim().isBefore(dashboard.inicio()));
    }

    // ------------------------------------------------------------------ //
    //  Apoio
    // ------------------------------------------------------------------ //

    /**
     * O dashboard faz quatro consultas, nesta ordem: o período consultado, o período
     * anterior de mesmo tamanho, a série histórica com lesão (para "dias sem acidente")
     * e os eventos em aberto (para o backlog). As duas últimas ignoram o período de
     * propósito — é o que faz esses dois painéis pararem de mentir.
     */
    private void responderCom(List<EventoSst> periodo, List<EventoSst> anterior) {
        responderCom(periodo, anterior,
                periodo.stream().filter(EventoSst::isComLesao).toList(),
                periodo.stream().filter(evento -> evento.getStatus().permiteEdicao()).toList());
    }

    @SuppressWarnings("unchecked")
    private void responderCom(List<EventoSst> periodo, List<EventoSst> anterior,
                              List<EventoSst> historicoComLesao, List<EventoSst> abertos) {
        when(repository.findAll(any(Specification.class)))
                .thenReturn((List) periodo)
                .thenReturn((List) anterior)
                .thenReturn((List) historicoComLesao)
                .thenReturn((List) abertos);
    }

    private static DashboardEventosSstFilter filtro() {
        return filtro(OffsetDateTime.now().minusDays(7), OffsetDateTime.now(), null);
    }

    private static DashboardEventosSstFilter filtro(OffsetDateTime inicio, OffsetDateTime fim,
                                                    GranularidadeDashboard granularidade) {
        return new DashboardEventosSstFilter(inicio, fim, granularidade,
                null, null, null, null, null, null, null, null);
    }

    private static EventoSst acidente(int diasPerdidos) {
        return evento(TipoEventoSst.ACIDENTE_TIPICO, ID_BASE, diasPerdidos);
    }

    private static EventoSst fatalidade() {
        return EventoSst.registrar("EVT-2026-000002",
                new DadosEventoSst(TipoEventoSst.ACIDENTE_TIPICO,
                        OffsetDateTime.now().minusHours(6), OffsetDateTime.now().minusHours(5),
                        ID_CLASSIFICACAO, null, RegistroEvidencia.NAO, RegistroEvidencia.NAO,
                        "Local", null, null, "Descrição do evento.",
                        ID_LOCAL_LESAO, null, null, 1,
                        true, TipoCat.COMUNICACAO_OBITO, "CAT-1", LocalDate.now()),
                new SnapshotAtividade(ID_ATIVIDADE, "Detonação", ID_GESTOR, "MARIA SOUZA", "Supervisora", false),
                new SnapshotOperacional(ID_BASE, "Base Central", null, null, null, null),
                List.of(new SnapshotTecnico(UUID.randomUUID(), "CARLA MENDES", "Técnica de Segurança")),
                List.of(SnapshotEnvolvido.colaborador(PapelEnvolvido.ACIDENTADO, UUID.randomUUID(),
                        "CARLOS NUNES", "Eletricista", "Base Central")));
    }

    private static PiramideAcidentalidadeItem nivel(List<PiramideAcidentalidadeItem> piramide,
                                                     String chave) {
        return piramide.stream().filter(item -> chave.equals(item.chave())).findFirst().orElseThrow();
    }

    private static EventoSst incidenteSemLesao() {
        return evento(TipoEventoSst.INCIDENTE_SEM_LESAO, ID_BASE, 0);
    }

    private static EventoSst incidenteNaBase(UUID idBase) {
        return evento(TipoEventoSst.INCIDENTE_SEM_LESAO, idBase, 0);
    }

    /** Mesmo evento, ocorrido numa data específica — para os painéis que olham a série. */
    private static EventoSst eventoEm(TipoEventoSst tipo, UUID idBase, OffsetDateTime ocorrencia) {
        boolean comLesao = tipo.implicaLesao();
        return EventoSst.registrar("EVT-2026-000099",
                new DadosEventoSst(tipo, ocorrencia, ocorrencia.plusHours(1),
                        ID_CLASSIFICACAO, null, RegistroEvidencia.NAO, RegistroEvidencia.NAO,
                        "Local", null, null, "Descrição do evento.",
                        comLesao ? ID_LOCAL_LESAO : null, null, null, comLesao ? 0 : null,
                        false, null, null, null),
                new SnapshotAtividade(ID_ATIVIDADE, "Detonação", ID_GESTOR, "MARIA SOUZA", "Supervisora", false),
                new SnapshotOperacional(idBase, "Base Central", null, null, null, null),
                List.of(new SnapshotTecnico(UUID.randomUUID(), "CARLA MENDES", "Técnica de Segurança")),
                comLesao
                        ? List.of(SnapshotEnvolvido.colaborador(PapelEnvolvido.ACIDENTADO, UUID.randomUUID(),
                        "CARLOS NUNES", "Eletricista", "Base Central"))
                        : List.of());
    }

    private static EventoSst evento(TipoEventoSst tipo, UUID idBase, int diasPerdidos) {
        boolean comLesao = tipo.implicaLesao();
        return EventoSst.registrar("EVT-2026-000001",
                new DadosEventoSst(tipo,
                        OffsetDateTime.now().minusHours(6), OffsetDateTime.now().minusHours(5),
                        ID_CLASSIFICACAO, null, RegistroEvidencia.NAO, RegistroEvidencia.NAO,
                        "Local", null, null, "Descrição do evento.",
                        comLesao ? ID_LOCAL_LESAO : null, null, null,
                        comLesao ? diasPerdidos : null,
                        false, null, null, null),
                new SnapshotAtividade(ID_ATIVIDADE, "Detonação", ID_GESTOR, "MARIA SOUZA", "Supervisora", false),
                new SnapshotOperacional(idBase, "Base Central", null, null, null, null),
                List.of(new SnapshotTecnico(UUID.randomUUID(), "CARLA MENDES", "Técnica de Segurança")),
                comLesao
                        ? List.of(SnapshotEnvolvido.colaborador(PapelEnvolvido.ACIDENTADO, UUID.randomUUID(),
                        "CARLOS NUNES", "Eletricista", "Base Central"))
                        : List.of());
    }
}
