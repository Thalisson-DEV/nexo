package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.execucao.application.dto.ComposicaoTurnoResponse;
import com.nexo.app.modules.execucao.application.mappers.TurnoMapper;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ComposicaoEquipeApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.execucao.domain.StatusTurno;
import com.nexo.app.modules.execucao.domain.Turno;
import com.nexo.app.modules.execucao.application.dto.AbrirTurnoRequest;
import com.nexo.app.shared.domain.events.TurnoAbertoEvent;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoUseCasesTest {

    @Mock
    private TurnoRepository repository;
    @Mock
    private TurnoMapper mapper;
    @Mock
    private UsuarioApi usuarioApi;
    @Mock
    private ComposicaoEquipeApi composicaoEquipeApi;
    @Mock
    private EquipeApi equipeApi;
    @Mock
    private ContratoApi contratoApi;
    @Mock
    private MetaDoCicloDaEquipe metaDoCicloDaEquipe;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TurnoUseCases useCases;

    private UUID idUsuario;
    private UUID idEquipe;
    private UUID idContrato;

    @BeforeEach
    void setUp() {
        idUsuario = UUID.randomUUID();
        idEquipe = UUID.randomUUID();
        idContrato = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(idUsuario.toString(), "credentials")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("buscarComposicaoMinhaEquipe deve retornar composicao, opcoes e acoes com valor vigente do contrato")
    void buscarComposicaoMinhaEquipe_comContrato_deveRetornarAcoesComValor() {
        ComposicaoEquipeApi.ComposicaoEquipe composicao = new ComposicaoEquipeApi.ComposicaoEquipe(
                idEquipe, "EQ-01", idUsuario, null, List.of(), new BigDecimal("22000.00"), new BigDecimal("1000.00")
        );
        ComposicaoEquipeApi.OpcoesComposicao opcoes = new ComposicaoEquipeApi.OpcoesComposicao(List.of(), List.of());
        List<ContratoApi.AcaoValorItem> acoesVigentes = List.of(
                new ContratoApi.AcaoValorItem(UUID.randomUUID(), "LIG01", "Ligação Nova", new BigDecimal("150.00")),
                new ContratoApi.AcaoValorItem(UUID.randomUUID(), "REL01", "Religação", new BigDecimal("75.50"))
        );

        when(equipeApi.buscarIdEquipePorUsuario(idUsuario)).thenReturn(Optional.of(idEquipe));
        when(composicaoEquipeApi.buscarComposicao(idEquipe)).thenReturn(Optional.of(composicao));
        when(composicaoEquipeApi.listarOpcoes(idEquipe)).thenReturn(opcoes);
        when(equipeApi.buscarContratoDaEquipe(idEquipe)).thenReturn(Optional.of(idContrato));
        when(contratoApi.listarAcoesComValor(eq(idContrato), any(LocalDate.class))).thenReturn(acoesVigentes);
        var metaDoCiclo = new com.nexo.app.modules.execucao.application.dto.MetaDoCicloResponse(
                com.nexo.app.modules.rh.PeriodicidadeMetaFaturamento.CICLO_COMERCIAL, "2026-10",
                LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 25), new BigDecimal("22000.00"),
                new BigDecimal("1000.00"), 22, new BigDecimal("9000.00"), java.time.OffsetDateTime.now());
        when(metaDoCicloDaEquipe.apurar(idEquipe)).thenReturn(Optional.of(metaDoCiclo));

        ComposicaoTurnoResponse response = useCases.buscarComposicaoMinhaEquipe();

        assertThat(response).isNotNull();
        assertThat(response.metaDoCiclo()).isEqualTo(metaDoCiclo);
        assertThat(response.composicao()).isEqualTo(composicao);
        assertThat(response.opcoes()).isEqualTo(opcoes);
        assertThat(response.usuarioResponsavel()).isTrue();
        assertThat(response.acoes()).hasSize(2);
        assertThat(response.acoes().get(0).codigo()).isEqualTo("LIG01");
        assertThat(response.acoes().get(0).valor()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.acoes().get(1).codigo()).isEqualTo("REL01");
        assertThat(response.acoes().get(1).valor()).isEqualByComparingTo(new BigDecimal("75.50"));
    }

    @Test
    @DisplayName("buscarComposicaoMinhaEquipe sem contrato vinculado deve retornar lista de acoes vazia")
    void buscarComposicaoMinhaEquipe_semContrato_deveRetornarAcoesVazia() {
        ComposicaoEquipeApi.ComposicaoEquipe composicao = new ComposicaoEquipeApi.ComposicaoEquipe(
                idEquipe, "EQ-02", UUID.randomUUID(), null, List.of(), new BigDecimal("15000.00"), new BigDecimal("681.82")
        );
        ComposicaoEquipeApi.OpcoesComposicao opcoes = new ComposicaoEquipeApi.OpcoesComposicao(List.of(), List.of());

        when(equipeApi.buscarIdEquipePorUsuario(idUsuario)).thenReturn(Optional.of(idEquipe));
        when(composicaoEquipeApi.buscarComposicao(idEquipe)).thenReturn(Optional.of(composicao));
        when(composicaoEquipeApi.listarOpcoes(idEquipe)).thenReturn(opcoes);
        when(equipeApi.buscarContratoDaEquipe(idEquipe)).thenReturn(Optional.empty());

        ComposicaoTurnoResponse response = useCases.buscarComposicaoMinhaEquipe();

        assertThat(response).isNotNull();
        assertThat(response.acoes()).isEmpty();
        assertThat(response.usuarioResponsavel()).isFalse();
        verifyNoInteractions(contratoApi);
    }

    @Test
    @DisplayName("fechar em turno aberto deve fechar e persistir")
    void fechar_turnoAberto_deveFecharESalvar() {
        UUID idTurno = UUID.randomUUID();
        Turno turno = new Turno(idEquipe, idUsuario);
        when(repository.findById(idTurno)).thenReturn(Optional.of(turno));

        useCases.fechar(idTurno);

        assertThat(turno.getStatus()).isEqualTo(StatusTurno.FECHADO);
        assertThat(turno.getDataFim()).isNotNull();
        verify(repository).save(turno);
    }

    @Test
    @DisplayName("fechar em turno ja fechado deve ser idempotente e nao re-salvar")
    void fechar_turnoJaFechado_deveSerIdempotente() {
        UUID idTurno = UUID.randomUUID();
        Turno turno = new Turno(idEquipe, idUsuario);
        turno.fechar();
        var dataFimOriginal = turno.getDataFim();
        when(repository.findById(idTurno)).thenReturn(Optional.of(turno));

        // Nao deve lancar excecao
        useCases.fechar(idTurno);

        assertThat(turno.getStatus()).isEqualTo(StatusTurno.FECHADO);
        assertThat(turno.getDataFim()).isEqualTo(dataFimOriginal);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("fechar em turno inexistente deve lancar RecursoNaoEncontradoException")
    void fechar_turnoInexistente_deveLancarExcecao() {
        UUID idTurno = UUID.randomUUID();
        when(repository.findById(idTurno)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCases.fechar(idTurno))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Turno não encontrado");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("abrir avisa os outros módulos que a equipe abriu o turno")
    void abrir_publicaTurnoAberto() {
        when(usuarioApi.isUsuarioCampo(idUsuario)).thenReturn(true);
        when(composicaoEquipeApi.buscarComposicao(idEquipe)).thenReturn(Optional.empty());
        when(repository.save(any(Turno.class))).thenAnswer(inv -> inv.getArgument(0));

        useCases.abrir(new AbrirTurnoRequest(idEquipe, null, null));

        verify(eventPublisher).publishEvent(argThat((Object e) -> e instanceof TurnoAbertoEvent t
                && t.idEquipe().equals(idEquipe) && t.dataInicio() != null));
    }
}
