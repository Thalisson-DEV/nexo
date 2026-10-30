package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.PainelSolicitacoesFilter;
import com.nexo.app.modules.estoque.infrastructure.persistence.PainelSolicitacoesRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.projections.SolicitacaoAnaliticaProjection;
import com.nexo.app.modules.obras.ObraApi;
import com.nexo.app.modules.rh.EquipeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PainelSolicitacoesUseCaseTest {

    @Mock private PainelSolicitacoesRepository repository;
    @Mock private EquipeApi equipeApi;
    @Mock private ObraApi obraApi;
    @InjectMocks private PainelSolicitacoesUseCase useCase;

    private static final UUID EQUIPE = UUID.randomUUID();

    private PainelSolicitacoesFilter filtro(UUID base, UUID equipe) {
        return new PainelSolicitacoesFilter(
                OffsetDateTime.parse("2026-10-01T00:00:00-03:00"),
                OffsetDateTime.parse("2026-10-13T23:59:59-03:00"),
                null, base, null, equipe, null);
    }

    private SolicitacaoAnaliticaProjection linha(String contexto, String status, String tipo, boolean backlog) {
        SolicitacaoAnaliticaProjection p = mock(SolicitacaoAnaliticaProjection.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        when(p.getContexto()).thenReturn(contexto);
        when(p.getStatus()).thenReturn(status);
        when(p.getTipoTecnico()).thenReturn(tipo);
        when(p.getDataSolicitacao()).thenReturn(OffsetDateTime.parse("2026-10-05T10:00:00-03:00").toInstant().toEpochMilli());
        // Mockito devolve 0 para Long não configurado; marcos ausentes são nulos no banco.
        when(p.getDataVerificacao()).thenReturn(null);
        when(p.getDataEntrega()).thenReturn(null);
        when(p.getSeparacaoIniciadaEm()).thenReturn(null);
        when(p.getSeparacaoConcluidaEm()).thenReturn(null);
        when(p.getEncerradaEm()).thenReturn(null);
        when(p.getItensComPendencia()).thenReturn(null);
        when(p.getIdEquipe()).thenReturn(EQUIPE);
        when(p.getItens()).thenReturn(2L);
        when(p.getQuantidadeSolicitada()).thenReturn(new BigDecimal("10"));
        when(p.getQuantidadeAtendida()).thenReturn(new BigDecimal("4"));
        when(p.getAnteriorAoPeriodo()).thenReturn(backlog);
        return p;
    }

    private void repositorioDevolve(List<SolicitacaoAnaliticaProjection> linhas) {
        when(repository.listarSolicitacoes(anyString(), anyString(), anyBoolean(), nullable(String.class), anyBoolean(),
                anyList(), nullable(String.class), anyInt())).thenReturn(linhas);
        when(repository.listarMateriais(anyString(), anyString(), nullable(String.class), anyBoolean(), anyList(),
                nullable(String.class), anyInt())).thenReturn(List.of());
        when(repository.resumirPeriodo(anyString(), anyString(), nullable(String.class), anyBoolean(), anyList(),
                nullable(String.class))).thenReturn(List.of());
        when(equipeApi.listarEquipesAtivas()).thenReturn(List.of());
        when(equipeApi.buscarPublicaPorId(EQUIPE)).thenReturn(Optional.of(
                new EquipeApi.EquipePublica(EQUIPE, "EQ-01", true, null, null, null, null)));
    }

    @Test
    void calculaEtapaComARegraDoKanbanEResolveAEquipe() {
        SolicitacaoAnaliticaProjection comercialPendente = linha("COMERCIAL", "PENDENTE", "221", false);
        SolicitacaoAnaliticaProjection tecnicaEntregue = linha("TECNICO", "ENTREGUE", "221", false);
        SolicitacaoAnaliticaProjection devolucaoAberta = linha("TECNICO", "APROVADO", "222", true);
        repositorioDevolve(List.of(comercialPendente, tecnicaEntregue, devolucaoAberta));

        var painel = useCase.obter(filtro(null, null));

        assertThat(painel.truncado()).isFalse();
        assertThat(painel.solicitacoes()).extracting(s -> s.etapa())
                .containsExactly("PENDENTE_APROVACAO", "CONCLUIDO", "PENDENTE_DEVOLUCAO");
        assertThat(painel.solicitacoes()).extracting(s -> s.tipoTecnico())
                .containsExactly(null, "221", "222");
        assertThat(painel.solicitacoes()).allSatisfy(s -> assertThat(s.equipe()).isEqualTo("EQ-01"));
        assertThat(painel.solicitacoes().get(2).anteriorAoPeriodo()).isTrue();
    }

    @Test
    void marcaTruncamentoQuandoORecortePassaDoLimite() {
        List<SolicitacaoAnaliticaProjection> muitas = new ArrayList<>();
        SolicitacaoAnaliticaProjection uma = linha("COMERCIAL", "ENTREGUE", null, false);
        for (int i = 0; i <= PainelSolicitacoesUseCase.LIMITE_SOLICITACOES; i++) muitas.add(uma);
        repositorioDevolve(muitas);

        var painel = useCase.obter(filtro(null, null));

        assertThat(painel.truncado()).isTrue();
        assertThat(painel.solicitacoes()).hasSize(PainelSolicitacoesUseCase.LIMITE_SOLICITACOES);
    }

    @Test
    void recorteSemEquipesNaoConsultaAsSolicitacoes() {
        UUID base = UUID.randomUUID();
        when(equipeApi.buscarIdsPorFiltros(eq(base), nullable(UUID.class))).thenReturn(List.of());

        var painel = useCase.obter(filtro(base, null));

        assertThat(painel.solicitacoes()).isEmpty();
        verify(repository, never()).listarSolicitacoes(anyString(), anyString(), anyBoolean(), nullable(String.class),
                anyBoolean(), anyList(), nullable(String.class), anyInt());
    }

    @Test
    void equipeForaDaBaseFiltradaEsvaziaORecorte() {
        UUID base = UUID.randomUUID();
        when(equipeApi.buscarIdsPorFiltros(eq(base), nullable(UUID.class))).thenReturn(List.of(UUID.randomUUID()));

        var painel = useCase.obter(filtro(base, EQUIPE));

        assertThat(painel.solicitacoes()).isEmpty();
    }
}
