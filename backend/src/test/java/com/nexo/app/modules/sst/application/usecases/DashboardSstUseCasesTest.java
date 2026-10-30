package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.sst.application.dto.ApuracaoMetaPeriodo;
import com.nexo.app.modules.sst.application.dto.DashboardDesviosResponse;
import com.nexo.app.modules.sst.application.dto.DashboardInspecoesResponse;
import com.nexo.app.modules.sst.application.dto.DashboardSstFilter;
import com.nexo.app.modules.sst.application.dto.InspetorMetaItem;
import com.nexo.app.modules.sst.application.dto.RankingItem;
import com.nexo.app.modules.sst.domain.EvidenciaInspecao;
import com.nexo.app.modules.sst.domain.GranularidadeDashboard;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.domain.StatusDesvio;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.TipoInspecaoRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardSstUseCasesTest {

    @Mock private InspecaoSegurancaRepository repository;
    @Mock private FormularioInspecaoRepository formularioRepository;
    @Mock private TipoInspecaoRepository tipoInspecaoRepository;
    @Mock private ApurarMetasPeriodoUseCase apurarMetas;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private ProcessoNegocioApi processoApi;
    @Mock private EquipeApi equipeApi;
    @Mock private BaseOperacionalApi baseOperacionalApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks
    private DashboardSstUseCases useCases;

    private final UUID inspetorA = UUID.randomUUID();
    private final UUID inspetorB = UUID.randomUUID();
    private final UUID inspetorC = UUID.randomUUID();
    private final UUID processoP1 = UUID.randomUUID();
    private final UUID processoP2 = UUID.randomUUID();
    private final UUID equipe1 = UUID.randomUUID();
    private final UUID equipe2 = UUID.randomUUID();
    private final UUID colabC1 = UUID.randomUUID();
    private final UUID colabC2 = UUID.randomUUID();
    private final UUID colabC3 = UUID.randomUUID();
    private final UUID pergunta1 = UUID.randomUUID();
    private final UUID pergunta2 = UUID.randomUUID();

    @BeforeEach
    void semMetaPorPadrao() {
        when(apurarMetas.apurar(any(), any(), any(), any(), any())).thenReturn(ApuracaoMetaPeriodo.vazia());
        when(apurarMetas.apurar(any(), any(), any(), any(), any(), any())).thenReturn(ApuracaoMetaPeriodo.vazia());
    }

    private InspecaoSeguranca novaInspecao(UUID inspetor, UUID processo, UUID equipe, OffsetDateTime data, List<UUID> colaboradores) {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                UUID.randomUUID(), UUID.randomUUID(), processo, inspetor, null, equipe, UUID.randomUUID(), data, null);
        colaboradores.forEach(inspecao::adicionarColaboradorInspecionado);
        inspecao.vincularEvidencia(new EvidenciaInspecao(
                inspetor, "foto.jpg", "image/jpeg", 3, "sst/inspecoes/evidencias/" + UUID.randomUUID() + ".jpg"));
        return inspecao;
    }

    private List<InspecaoSeguranca> cenario() {
        OffsetDateTime dia1 = OffsetDateTime.now().minusDays(1);
        OffsetDateTime dia2 = OffsetDateTime.now();

        InspecaoSeguranca i1 = novaInspecao(inspetorA, processoP1, equipe1, dia1, List.of(colabC1, colabC2));
        i1.registrarDesvio(pergunta1, UUID.randomUUID(), GravidadeDesvio.ALTA, "d", "a", null, List.of(colabC1));
        i1.concluir();

        InspecaoSeguranca i2 = novaInspecao(inspetorA, processoP1, equipe1, dia1, List.of(colabC1));
        i2.concluir();

        InspecaoSeguranca i3 = novaInspecao(inspetorB, processoP2, equipe2, dia2, List.of(colabC3));
        i3.registrarDesvio(pergunta1, UUID.randomUUID(), GravidadeDesvio.CRITICA, "d", "a", null, List.of(colabC3));
        i3.registrarDesvio(pergunta2, UUID.randomUUID(), GravidadeDesvio.BAIXA, "d", "a", null, List.of(colabC3));
        i3.concluir();

        return List.of(i1, i2, i3);
    }

    private DashboardSstFilter filtroPeriodo() {
        return new DashboardSstFilter(
                OffsetDateTime.now().minusDays(5), OffsetDateTime.now().plusDays(1),
                null, null, null, null, null, null, null, null, null, null);
    }

    private RankingItem porId(List<RankingItem> ranking, UUID id) {
        return ranking.stream().filter(r -> id.equals(r.id())).findFirst().orElse(null);
    }

    private InspetorMetaItem inspetor(DashboardInspecoesResponse r, UUID id) {
        return r.porInspetor().stream().filter(i -> i.idInspetor().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Dashboard de inspeÃ§Ãµes deve consolidar totais, mÃ©dias, percentuais e distribuiÃ§Ãµes")
    void dashboardInspecoes() {
        when(repository.findAll(any(Specification.class))).thenReturn(cenario());

        DashboardInspecoesResponse r = useCases.gerarDashboardInspecoes(filtroPeriodo());
        var resumo = r.resumo();

        assertEquals(3, resumo.totalInspecoes());
        assertEquals(2, resumo.inspecoesComDesvio());
        assertEquals(1, resumo.inspecoesSemDesvio());
        assertEquals(3, resumo.totalDesvios());
        assertEquals(3, resumo.colaboradoresInspecionados());   // C1, C2, C3
        assertEquals(2, resumo.inspetoresAtivos());             // A, B

        assertEquals(0, new BigDecimal("33.33").compareTo(resumo.percentualSemDesvio()));
        assertEquals(0, new BigDecimal("1.00").compareTo(resumo.mediaDesviosPorInspecao()));
        assertEquals(0, new BigDecimal("1.50").compareTo(resumo.mediaInspecoesPorInspetor()));

        // DistribuiÃ§Ãµes por dimensÃ£o
        assertEquals(2, porId(r.porProcesso(), processoP1).total());

        // Comparativo por inspetor: A (2) na frente de B (1)
        assertEquals(inspetorA, r.porInspetor().get(0).idInspetor());
        assertEquals(2, r.porInspetor().get(0).realizado());
        assertEquals(1, r.porInspetor().get(0).desvios());

        // Sem meta cadastrada o bloco de metas vem desligado, mas com o realizado preenchido
        assertFalse(r.meta().possuiMeta());
        assertEquals(3, r.meta().realizadoPeriodo());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.meta().percentualAtingido()));

        // A sÃ©rie cobre o perÃ­odo inteiro e soma as 3 inspeÃ§Ãµes
        assertEquals(3, r.serieTemporal().stream().mapToLong(SerieTotal -> SerieTotal.realizado()).sum());
        assertEquals(GranularidadeDashboard.DIA, r.granularidade());
    }

    @Test
    @DisplayName("Indicadores de equipe devem trazer volume, conformidade e tempo sem inspeÃ§Ã£o")
    void indicadoresPorEquipe() {
        when(repository.findAll(any(Specification.class))).thenReturn(cenario());

        DashboardInspecoesResponse r = useCases.gerarDashboardInspecoes(filtroPeriodo());

        var e1 = r.porEquipe().stream().filter(e -> e.idEquipe().equals(equipe1)).findFirst().orElseThrow();
        assertEquals(2, e1.inspecoes());
        assertEquals(1, e1.desvios());
        assertEquals(0, new BigDecimal("0.50").compareTo(e1.desviosPorInspecao()));
        assertEquals(0, new BigDecimal("50.00").compareTo(e1.percentualSemDesvio()));
        assertEquals(2, e1.colaboradoresInspecionados(), "C1 e C2, sem contar o vÃ­nculo repetido");

        // A equipe 1 foi inspecionada ontem; o perÃ­odo termina amanhÃ£
        assertEquals(2, e1.diasSemInspecao());

        var e2 = r.porEquipe().stream().filter(e -> e.idEquipe().equals(equipe2)).findFirst().orElseThrow();
        assertEquals(1, e2.inspecoes());
        assertEquals(2, e2.desvios());
        assertEquals(0, BigDecimal.ZERO.compareTo(e2.percentualSemDesvio()), "a Ãºnica inspeÃ§Ã£o teve desvio");

        // Ordenado por volume
        assertEquals(equipe1, r.porEquipe().get(0).idEquipe());
    }

    @Test
    @DisplayName("Indicadores de colaborador devem medir frequÃªncia, desvios recebidos e cobertura")
    void indicadoresPorColaborador() {
        when(repository.findAll(any(Specification.class))).thenReturn(cenario());
        when(colaboradorApi.contarAtivos(any(), any())).thenReturn(10L);

        DashboardInspecoesResponse r = useCases.gerarDashboardInspecoes(filtroPeriodo());

        // C1 aparece em 2 inspeÃ§Ãµes e recebeu 1 desvio
        var c1 = r.porColaborador().stream()
                .filter(c -> c.idColaborador().equals(colabC1)).findFirst().orElseThrow();
        assertEquals(2, c1.vezesInspecionado());
        assertEquals(1, c1.desviosRecebidos());
        assertEquals(colabC1, r.porColaborador().get(0).idColaborador(), "ordenado por frequÃªncia");

        // C3 recebeu os 2 desvios da mesma inspeÃ§Ã£o
        var c3 = r.porColaborador().stream()
                .filter(c -> c.idColaborador().equals(colabC3)).findFirst().orElseThrow();
        assertEquals(1, c3.vezesInspecionado());
        assertEquals(2, c3.desviosRecebidos());

        var cobertura = r.colaboradores();
        assertEquals(3, cobertura.colaboradoresInspecionados());
        assertEquals(10, cobertura.colaboradoresAtivos());
        assertEquals(0, new BigDecimal("30.00").compareTo(cobertura.percentualCobertura()));
        assertEquals(7, cobertura.colaboradoresSemInspecao());
        assertEquals(2, cobertura.colaboradoresComDesvio());       // C1 e C3
        assertEquals(1, cobertura.colaboradoresReincidentes());    // C3, com 2 desvios
    }

    @Test
    @DisplayName("Dashboard deve comparar realizado com a meta do perÃ­odo, por total e por inspetor")
    void dashboardInspecoesComMeta() {
        when(repository.findAll(any(Specification.class))).thenReturn(cenario());
        when(apurarMetas.apurar(any(), any(), any(), any(), any(), any())).thenReturn(new ApuracaoMetaPeriodo(
                new BigDecimal("6.000000"),
                Map.of(LocalDate.now(), new BigDecimal("6.000000")),
                Map.of(inspetorA, new BigDecimal("2.000000"), inspetorC, new BigDecimal("4.000000")),
                2, 1));

        DashboardInspecoesResponse r = useCases.gerarDashboardInspecoes(filtroPeriodo());
        var meta = r.meta();

        assertTrue(meta.possuiMeta());
        assertEquals(0, new BigDecimal("6.00").compareTo(meta.metaPeriodo()));
        assertEquals(3, meta.realizadoPeriodo());
        assertEquals(0, new BigDecimal("50.00").compareTo(meta.percentualAtingido()));
        assertEquals(0, new BigDecimal("3.00").compareTo(meta.saldoParaMeta()));
        assertEquals(2, meta.colaboradoresComMeta());
        assertEquals(1, meta.colaboradoresSemAcesso());

        // A cumpriu a prÃ³pria meta (2 de 2); C nÃ£o lanÃ§ou nada
        assertEquals(1, meta.inspetoresNaMeta());
        assertEquals(0, new BigDecimal("50.00").compareTo(meta.percentualInspetoresNaMeta()));

        InspetorMetaItem a = inspetor(r, inspetorA);
        assertTrue(a.atingiuMeta());
        assertEquals(0, new BigDecimal("100.00").compareTo(a.percentualAtingido()));

        // Inspetor com meta e sem nenhuma inspeÃ§Ã£o precisa aparecer â€” Ã© o caso a cobrar
        InspetorMetaItem c = inspetor(r, inspetorC);
        assertEquals(0, c.realizado());
        assertEquals(0, new BigDecimal("4.00").compareTo(c.meta()));
        assertFalse(c.atingiuMeta());

        // Inspetor sem meta entra no comparativo com meta zero e percentual nulo
        InspetorMetaItem b = inspetor(r, inspetorB);
        assertEquals(0, BigDecimal.ZERO.compareTo(b.meta()));
        assertNull(b.percentualAtingido());
    }

    @Test
    @DisplayName("A sÃ©rie deve respeitar a granularidade e devolver todos os baldes do perÃ­odo")
    void serieRespeitaGranularidade() {
        OffsetDateTime janeiro = OffsetDateTime.parse("2026-01-15T09:00:00-03:00");
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(
                novaInspecao(inspetorA, processoP1, equipe1, janeiro, List.of(colabC1))));
        when(apurarMetas.apurar(any(), any(), any(), any(), any(), any())).thenReturn(new ApuracaoMetaPeriodo(
                new BigDecimal("4.000000"),
                Map.of(LocalDate.of(2026, 1, 20), new BigDecimal("4.000000")),
                Map.of(inspetorA, new BigDecimal("4.000000")), 1, 0));

        DashboardSstFilter trimestre = new DashboardSstFilter(
                OffsetDateTime.parse("2026-01-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-03-31T23:59:59-03:00"),
                null, null, null, null, null, null, null, null, null, null);

        DashboardInspecoesResponse mensal = useCases.gerarDashboardInspecoes(trimestre);

        assertEquals(GranularidadeDashboard.MES, mensal.granularidade(), "90 dias devem cair em recorte mensal");
        assertEquals(3, mensal.serieTemporal().size(), "fevereiro e marÃ§o entram zerados");
        assertEquals("2026-01", mensal.serieTemporal().get(0).chave());
        assertEquals("jan/2026", mensal.serieTemporal().get(0).rotulo());
        assertEquals(1, mensal.serieTemporal().get(0).realizado());
        assertEquals(0, new BigDecimal("4.00").compareTo(mensal.serieTemporal().get(0).meta()));
        assertEquals(0, new BigDecimal("25.00").compareTo(mensal.serieTemporal().get(0).percentualAtingido()));
        assertEquals(0, mensal.serieTemporal().get(1).realizado());

        DashboardSstFilter anual = new DashboardSstFilter(
                trimestre.inicio(), trimestre.fim(), null, null, null, null, null, null, null, null, null,
                GranularidadeDashboard.ANO);

        DashboardInspecoesResponse porAno = useCases.gerarDashboardInspecoes(anual);

        assertEquals(1, porAno.serieTemporal().size());
        assertEquals("2026", porAno.serieTemporal().get(0).chave());
        assertEquals(1, porAno.serieTemporal().get(0).realizado());
    }

    @Test
    @DisplayName("Dashboard de desvios deve consolidar gravidades, status, reincidÃªncia e rankings")
    void dashboardDesvios() {
        when(repository.findAll(any(Specification.class))).thenReturn(cenario());

        DashboardDesviosResponse r = useCases.gerarDashboardDesvios(filtroPeriodo());

        assertEquals(3, r.totalDesvios());
        assertEquals(3, r.totalAbertos());
        assertEquals(0, r.totalResolvidos());
        assertEquals(2, r.totalCriticos());                    // ALTA + CRITICA
        assertEquals(1L, r.totalPorGravidade().get(GravidadeDesvio.ALTA));
        assertEquals(1L, r.totalPorGravidade().get(GravidadeDesvio.CRITICA));
        assertEquals(1L, r.totalPorGravidade().get(GravidadeDesvio.BAIXA));

        assertEquals(2, r.totalColaboradoresEnvolvidos());     // C1, C3
        assertEquals(1, r.totalColaboradoresReincidentes());   // C3 (2 desvios)
        assertEquals(2, r.totalInspecoesComDesvio());

        assertEquals(0, new BigDecimal("66.67").compareTo(r.percentualCriticos()));
        assertEquals(0, new BigDecimal("50.00").compareTo(r.percentualReincidencia()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.taxaResolucao()));

        // Ranking de colaboradores: C3 (2) na frente de C1 (1)
        assertEquals(colabC3, r.rankingColaboradores().get(0).id());
        assertEquals(2, r.rankingColaboradores().get(0).total());

        // Ranking de perguntas: pergunta1 aparece em 2 desvios
        assertEquals(2, porId(r.rankingPerguntas(), pergunta1).total());
        assertEquals(1, porId(r.rankingPerguntas(), pergunta2).total());

        assertEquals(3, r.backlogPorIdade().stream().mapToLong(f -> f.total()).sum());
        assertEquals(3, r.serieDesvios().getLast().backlogFimDoDia());
        assertTrue(r.prioridades().stream().anyMatch(p -> p.tipo().equals("SEM_PRAZO")));
    }

    @Test
    @DisplayName("Tendência geral deve manter resolvidos na data da ocorrência e preencher baldes vazios")
    void tendenciaGeralIncluiResolvidosEPreencheBaldes() {
        OffsetDateTime dataInspecao = OffsetDateTime.parse("2026-01-15T09:00:00-03:00");
        InspecaoSeguranca inspecao = novaInspecao(
                inspetorA, processoP1, equipe1, dataInspecao, List.of(colabC1));
        var desvio = inspecao.registrarDesvio(
                pergunta1, UUID.randomUUID(), GravidadeDesvio.ALTA, "d", "a", null, List.of(colabC1));
        desvio.atualizarTratamento(StatusDesvio.RESOLVIDO, "corrigido", null);
        inspecao.concluir();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(inspecao));

        DashboardSstFilter trimestre = new DashboardSstFilter(
                OffsetDateTime.parse("2026-01-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-03-31T23:59:59-03:00"),
                null, null, null, null, null, null, null, null, null, null);

        DashboardDesviosResponse mensal = useCases.gerarDashboardDesvios(trimestre);

        assertEquals(1, mensal.totalDesvios(), "o status resolvido não apaga a ocorrência");
        assertEquals(1, mensal.totalResolvidos());
        assertEquals(GranularidadeDashboard.MES, mensal.granularidade());
        assertEquals(3, mensal.tendenciaDesvios().size());
        assertEquals("2026-01", mensal.tendenciaDesvios().get(0).chave());
        assertEquals(1, mensal.tendenciaDesvios().get(0).totalDesvios());
        assertEquals(0, mensal.tendenciaDesvios().get(1).totalDesvios());
        assertEquals(0, mensal.tendenciaDesvios().get(2).totalDesvios());

        DashboardSstFilter anual = new DashboardSstFilter(
                trimestre.inicio(), trimestre.fim(), null, null, null, null, null, null, null, null, null,
                GranularidadeDashboard.ANO);
        DashboardDesviosResponse porAno = useCases.gerarDashboardDesvios(anual);

        assertEquals(GranularidadeDashboard.ANO, porAno.granularidade());
        assertEquals(1, porAno.tendenciaDesvios().size());
        assertEquals("2026", porAno.tendenciaDesvios().get(0).chave());
        assertEquals(1, porAno.tendenciaDesvios().get(0).totalDesvios());
    }

    @Test
    @DisplayName("Comparativos gerais devem incluir resolvidos e separar a taxa de resolução")
    void indicadoresGeraisIncluemResolvidos() {
        InspecaoSeguranca atual = novaInspecao(
                inspetorA, processoP1, equipe1,
                OffsetDateTime.parse("2026-08-10T09:00:00-03:00"), List.of(colabC1));
        var criticoResolvido = atual.registrarDesvio(
                pergunta1, UUID.randomUUID(), GravidadeDesvio.CRITICA, "d", "a", null, List.of(colabC1));
        criticoResolvido.atualizarTratamento(StatusDesvio.RESOLVIDO, "corrigido", null);
        atual.registrarDesvio(
                pergunta2, UUID.randomUUID(), GravidadeDesvio.BAIXA, "d", "a", null, List.of(colabC1));
        atual.concluir();

        InspecaoSeguranca anterior = novaInspecao(
                inspetorA, processoP1, equipe1,
                OffsetDateTime.parse("2026-07-10T09:00:00-03:00"), List.of(colabC1));
        var resolvidoAnterior = anterior.registrarDesvio(
                pergunta1, UUID.randomUUID(), GravidadeDesvio.BAIXA, "d", "a", null, List.of(colabC1));
        resolvidoAnterior.atualizarTratamento(StatusDesvio.RESOLVIDO, "corrigido", null);
        anterior.concluir();

        when(repository.findAll(any(Specification.class)))
                .thenReturn(List.of(atual))
                .thenReturn(List.of(anterior))
                .thenReturn(List.of(anterior, atual));

        DashboardSstFilter agosto = new DashboardSstFilter(
                OffsetDateTime.parse("2026-08-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-08-31T23:59:59-03:00"),
                null, null, null, null, null, null, null, null, null, null);

        DashboardDesviosResponse r = useCases.gerarDashboardDesvios(agosto);

        assertEquals(0, new BigDecimal("2.00").compareTo(r.indicadores().get("totalDesvios").valor()));
        assertEquals(0, new BigDecimal("1.00").compareTo(r.indicadores().get("totalDesvios").anterior()));
        assertEquals(0, new BigDecimal("100.00").compareTo(r.indicadores().get("totalDesvios").variacaoPercentual()));
        assertEquals(0, new BigDecimal("1.00").compareTo(r.indicadores().get("desviosCriticos").valor()));
        assertEquals(0, new BigDecimal("1.00").compareTo(r.indicadores().get("inspecoesComDesvio").valor()));
        assertEquals(0, new BigDecimal("50.00").compareTo(r.indicadores().get("taxaResolucao").valor()));
        assertEquals(0, new BigDecimal("100.00").compareTo(r.indicadores().get("taxaResolucao").anterior()));
        assertTrue(r.indicadores().containsKey("backlog"), "indicadores operacionais legados continuam disponíveis");
        assertTrue(r.indicadores().containsKey("vencidos"));
    }

    @Test
    @DisplayName("Filtro explícito de status deve recortar a visão sem alterar o padrão geral")
    void filtroDeStatusRecortaSomenteQuandoInformado() {
        InspecaoSeguranca inspecao = novaInspecao(
                inspetorA, processoP1, equipe1, OffsetDateTime.now(), List.of(colabC1));
        inspecao.registrarDesvio(
                pergunta1, UUID.randomUUID(), GravidadeDesvio.ALTA, "aberto", "a", null, List.of(colabC1));
        var resolvido = inspecao.registrarDesvio(
                pergunta2, UUID.randomUUID(), GravidadeDesvio.BAIXA, "resolvido", "a", null, List.of(colabC1));
        resolvido.atualizarTratamento(StatusDesvio.RESOLVIDO, "corrigido", null);
        inspecao.concluir();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(inspecao));

        DashboardDesviosResponse geral = useCases.gerarDashboardDesvios(filtroPeriodo());
        DashboardSstFilter apenasAbertos = new DashboardSstFilter(
                filtroPeriodo().inicio(), filtroPeriodo().fim(),
                null, null, null, null, null, null, null, null, List.of(StatusDesvio.ABERTO), null);
        DashboardDesviosResponse filtrado = useCases.gerarDashboardDesvios(apenasAbertos);

        assertEquals(2, geral.totalDesvios());
        assertEquals(1, geral.totalResolvidos());
        assertEquals(2, geral.tendenciaDesvios().stream().mapToLong(item -> item.totalDesvios()).sum());
        assertEquals(1, filtrado.totalDesvios());
        assertEquals(1, filtrado.totalAbertos());
        assertEquals(0, filtrado.totalResolvidos());
        assertEquals(1, filtrado.tendenciaDesvios().stream().mapToLong(item -> item.totalDesvios()).sum());
        assertEquals(0, new BigDecimal("1.00").compareTo(filtrado.indicadores().get("totalDesvios").valor()));
    }

    @Test
    @DisplayName("Período mensal completo deve comparar o total com o mês-calendário anterior")
    void comparaMesCalendarioAnterior() {
        OffsetDateTime julho = OffsetDateTime.parse("2026-07-10T09:00:00-03:00");
        OffsetDateTime junho = OffsetDateTime.parse("2026-06-10T09:00:00-03:00");
        when(repository.findAll(any(Specification.class)))
                .thenReturn(List.of(
                        novaInspecao(inspetorA, processoP1, equipe1, julho, List.of(colabC1)),
                        novaInspecao(inspetorA, processoP1, equipe1, julho, List.of(colabC1))))
                .thenReturn(List.of(novaInspecao(inspetorA, processoP1, equipe1, junho, List.of(colabC1))));

        DashboardSstFilter filtro = new DashboardSstFilter(
                OffsetDateTime.parse("2026-07-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-07-31T23:59:59-03:00"),
                null, null, null, null, null, null, null, null, null, null);

        var resumo = useCases.gerarDashboardInspecoes(filtro).resumo();

        assertEquals(2, resumo.totalInspecoes());
        assertEquals(1, resumo.totalPeriodoAnterior());
        assertEquals(0, new BigDecimal("100.00").compareTo(resumo.variacaoPercentual()));
    }

    @Test
    @DisplayName("Volume por gravidade deve calcular distribuição e comparar com período anterior")
    void volumePorGravidadeComparaPeriodoAnterior() {
        OffsetDateTime atualData = OffsetDateTime.parse("2026-08-10T09:00:00-03:00");
        InspecaoSeguranca atual = novaInspecao(inspetorA, processoP1, equipe1, atualData, List.of(colabC1));
        atual.registrarDesvio(pergunta1, UUID.randomUUID(), GravidadeDesvio.ALTA, "d1", "a", null, List.of(colabC1));
        atual.registrarDesvio(pergunta2, UUID.randomUUID(), GravidadeDesvio.ALTA, "d2", "a", null, List.of(colabC1));
        atual.registrarDesvio(pergunta1, UUID.randomUUID(), GravidadeDesvio.BAIXA, "d3", "a", null, List.of(colabC1));
        atual.concluir();

        OffsetDateTime antData = OffsetDateTime.parse("2026-07-10T09:00:00-03:00");
        InspecaoSeguranca anterior = novaInspecao(inspetorA, processoP1, equipe1, antData, List.of(colabC1));
        anterior.registrarDesvio(pergunta1, UUID.randomUUID(), GravidadeDesvio.ALTA, "d4", "a", null, List.of(colabC1));
        anterior.concluir();

        when(repository.findAll(any(Specification.class)))
                .thenReturn(List.of(atual))
                .thenReturn(List.of(anterior))
                .thenReturn(List.of(anterior, atual));

        DashboardSstFilter filtro = new DashboardSstFilter(
                OffsetDateTime.parse("2026-08-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-08-31T23:59:59-03:00"),
                null, null, null, null, null, null, null, null, null, null);

        DashboardDesviosResponse r = useCases.gerarDashboardDesvios(filtro);

        assertEquals(4, r.volumePorGravidade().size());

        var alta = r.volumePorGravidade().stream().filter(v -> v.gravidade() == GravidadeDesvio.ALTA).findFirst().orElseThrow();
        assertEquals(2, alta.total());
        assertEquals(1, alta.anterior());
        assertEquals(0, new BigDecimal("66.67").compareTo(alta.percentual()));
        assertEquals(0, new BigDecimal("100.00").compareTo(alta.variacaoPercentual()));

        var baixa = r.volumePorGravidade().stream().filter(v -> v.gravidade() == GravidadeDesvio.BAIXA).findFirst().orElseThrow();
        assertEquals(1, baixa.total());
        assertEquals(0, baixa.anterior());
        assertEquals(0, new BigDecimal("33.33").compareTo(baixa.percentual()));

        var media = r.volumePorGravidade().stream().filter(v -> v.gravidade() == GravidadeDesvio.MEDIA).findFirst().orElseThrow();
        assertEquals(0, media.total());
        assertEquals(0, media.anterior());
        assertEquals(0, BigDecimal.ZERO.compareTo(media.percentual()));

        var critica = r.volumePorGravidade().stream().filter(v -> v.gravidade() == GravidadeDesvio.CRITICA).findFirst().orElseThrow();
        assertEquals(0, critica.total());
        assertEquals(0, critica.anterior());
        assertEquals(0, BigDecimal.ZERO.compareTo(critica.percentual()));
    }
}
