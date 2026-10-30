package com.nexo.app.modules.chamado.application.usecases;

import com.nexo.app.modules.chamado.application.dto.DashboardChamadosFilter;
import com.nexo.app.modules.chamado.application.dto.DashboardChamadosResponse;
import com.nexo.app.modules.chamado.domain.*;
import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.EquipeAtendimentoRepository;
import com.nexo.app.modules.chamado.infrastructure.persistence.TopicoChamadoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardChamadosUseCasesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Mock private ChamadoRepository repository;
    @Mock private TopicoChamadoRepository topicoRepository;
    @Mock private EquipeAtendimentoRepository equipeRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ColaboradorApi colaboradorApi;

    @InjectMocks
    private DashboardChamadosUseCases useCases;

    private EquipeAtendimento equipeTI;
    private TopicoChamado topicoRede;
    private TopicoChamado topicoSoftware;
    private UUID solicitanteA;
    private UUID atendenteB;

    @BeforeEach
    void setUp() {
        solicitanteA = UUID.randomUUID();
        atendenteB = UUID.randomUUID();

        equipeTI = new EquipeAtendimento("TI", "Suporte de TI", "Equipe responsável", Set.of(atendenteB));
        topicoRede = new TopicoChamado("REDES", "Infra de Redes", "Problemas de conectividade",
                PrioridadeChamado.ALTA, equipeTI, 4, 24, true, 48, 1);
        topicoSoftware = new TopicoChamado("SISTEMA", "Sistema Nexo", "Dúvidas ou correções",
                PrioridadeChamado.NORMAL, equipeTI, 2, 8, true, 48, 2);

        when(colaboradorApi.buscarNomeCompletoPorUsuario(any())).thenReturn(Optional.empty());
        when(usuarioApi.buscarNomePorId(atendenteB)).thenReturn(Optional.of("Carlos Atendente"));
        when(usuarioApi.buscarNomePorId(solicitanteA)).thenReturn(Optional.of("Maria Solicitante"));
    }

    @Test
    @DisplayName("Gera dashboard resiliente com listas vazias sem divisão por zero")
    void deveGerarDashboardVazioSemErro() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());

        DashboardChamadosResponse resp = useCases.gerar(DashboardChamadosFilter.vazio());

        assertNotNull(resp);
        assertEquals(0, resp.totalChamados());
        assertEquals(0, resp.totalPendentes());
        assertEquals(BigDecimal.ZERO.setScale(2), resp.taxaResolucao());
        assertEquals(BigDecimal.ZERO.setScale(2), resp.taxaSlaResolucaoPercentual());
        assertNotNull(resp.prioridades());
        assertFalse(resp.prioridades().isEmpty());
        assertEquals("OPERACAO_REGULAR", resp.prioridades().getFirst().tipo());
    }

    @Test
    @DisplayName("Calcula taxas, percentuais, SLAs e aging do backlog com chamados reais")
    void deveCalcularMetricasCorretamente() {
        OffsetDateTime agora = OffsetDateTime.now(ZONE);
        OffsetDateTime inicio = agora.minusDays(7);
        OffsetDateTime fim = agora;

        // Chamado 1: Crítico, aberto, sem responsável (gera prioridade gerencial)
        Chamado c1 = new Chamado(topicoRede, "Switch queimado", "Link principal fora do ar",
                PrioridadeChamado.CRITICA, solicitanteA);

        // Chamado 2: Normal, em atendimento, atribuído
        Chamado c2 = new Chamado(topicoSoftware, "Bug de tela", "Erro 500 no formulário",
                PrioridadeChamado.NORMAL, solicitanteA);
        c2.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteB, null, atendenteB);

        // Chamado 3: Resolvido dentro do prazo
        Chamado c3 = new Chamado(topicoSoftware, "Ajuste de perfil", "Liberar permissão",
                PrioridadeChamado.BAIXA, solicitanteA);
        c3.atualizarAtendimento(StatusChamado.EM_ATENDIMENTO, null, null, atendenteB, null, atendenteB);
        c3.atualizarAtendimento(StatusChamado.RESOLVIDO, null, null, atendenteB, "Permissão concedida", atendenteB);

        List<Chamado> cohort = List.of(c1, c2, c3);
        List<Chamado> backlog = List.of(c1, c2);

        when(repository.findAll(any(Specification.class))).thenAnswer(inv -> {
            // Se chamado para noEscopo com intervalo
            return cohort;
        });
        when(repository.findIdsComReabertura(any())).thenReturn(Set.of());

        DashboardChamadosFilter filter = new DashboardChamadosFilter(
                inicio, fim, null, null, null, null, null, null, null, GranularidadeDashboard.DIA);

        DashboardChamadosResponse resp = useCases.gerar(filter);

        assertNotNull(resp);
        assertEquals(3, resp.totalChamados());
        assertEquals(1, resp.totalResolvidos());
        assertEquals(1, resp.totalAbertos());
        assertEquals(1, resp.totalEmAtendimento());
        assertEquals(2, resp.totalPendentes());
        assertEquals(1, resp.totalCriticos());

        // Taxa de resolução: 1 resolvido em 3 = 33.33%
        assertEquals(new BigDecimal("33.33"), resp.taxaResolucao());

        // Verificação do indicador no mapa
        assertTrue(resp.indicadores().containsKey("totalChamados"));
        assertTrue(resp.indicadores().containsKey("taxaResolucao"));
        assertTrue(resp.indicadores().containsKey("chamadosCriticos"));

        // Verificação do aging do backlog
        assertNotNull(resp.backlogPorIdade());
        assertFalse(resp.backlogPorIdade().isEmpty());

        // Verificação das prioridades gerenciais: c1 é crítico e está ABERTO sem responsável
        assertTrue(resp.prioridades().stream().anyMatch(p -> "CRITICOS_SEM_ATRIBUICAO".equals(p.tipo())));

        // Desempenho por Tópico e Equipe
        assertFalse(resp.desempenhoPorTopico().isEmpty());
        assertFalse(resp.desempenhoPorEquipe().isEmpty());
    }

    @Test
    @DisplayName("Valida rejeição de intervalo com data fim anterior a data início")
    void deveValidarPeriodoInvalido() {
        OffsetDateTime agora = OffsetDateTime.now(ZONE);
        DashboardChamadosFilter filter = new DashboardChamadosFilter(
                agora, agora.minusDays(5), null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> useCases.gerar(filter));
    }
}
