package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.*;
import com.nexo.app.modules.obras.application.mappers.ObraMapper;
import com.nexo.app.modules.obras.domain.Obra;
import com.nexo.app.modules.obras.domain.StatusObra;
import com.nexo.app.modules.obras.domain.TipoObra;
import com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto;
import com.nexo.app.modules.obras.domain.TipoVinculoProjeto;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.rh.*;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObraUseCasesTest {

    @Mock
    private ObraRepository repository;

    @Mock
    private StatusObraUseCases statusUseCases;

    @Mock
    private AgenteResponsavelUseCases agenteUseCases;

    @Mock
    private ProcessoNegocioApi processoNegocioApi;

    @Mock
    private BaseOperacionalApi baseOperacionalApi;

    @Mock
    private MunicipioApi municipioApi;

    @Mock
    private CentroFaturamentoApi centroFaturamentoApi;

    @Mock
    private SupervisorApi supervisorApi;

    @Mock
    private CoordenadorApi coordenadorApi;

    @Mock
    private ObraMapper mapper;

    @Mock
    private RelacionamentoProjetoUseCases relacionamentoProjetoUseCases;

    private NormalizadorObraService normalizador;
    private ObraUseCases useCases;

    @BeforeEach
    void setUp() {
        normalizador = new NormalizadorObraService();
        useCases = new ObraUseCases(
                repository,
                statusUseCases,
                agenteUseCases,
                normalizador,
                processoNegocioApi,
                baseOperacionalApi,
                municipioApi,
                centroFaturamentoApi,
                supervisorApi,
                coordenadorApi,
                mapper,
                relacionamentoProjetoUseCases
        );
    }

    @Test
    @DisplayName("Deve criar obra com sucesso e normalizar número e título")
    void deveCriarObraComSucesso() {
        UUID idStatus = UUID.randomUUID();
        StatusObra status = new StatusObra("EM EXECUÇÃO", 1, false);
        when(statusUseCases.buscarEntidade(idStatus)).thenReturn(status);
        when(repository.existsByNumeroIgnoreCaseAndAtivoTrue("1228694")).thenReturn(false);

        UUID idProcesso = UUID.randomUUID();
        UUID idBaseOperacional = UUID.randomUUID();
        UUID idMunicipio = UUID.randomUUID();
        UUID idCentroCusto = UUID.randomUUID();
        UUID idSupervisor = UUID.randomUUID();
        UUID idCoordenador = UUID.randomUUID();

        when(supervisorApi.existeSupervisor(idSupervisor)).thenReturn(true);
        when(coordenadorApi.existeCoordenador(idCoordenador)).thenReturn(true);

        ObraRequest request = new ObraRequest(
                "B-1228694",
                "rede pedagogica remanso (concessionaria)",
                "Descricao",
                "9101923953",
                new BigDecimal("150000.00"),
                LocalDate.of(2025, 11, 3),
                LocalDate.of(2025, 11, 30),
                null,
                -9.439007,
                -41.968228,
                idProcesso, idBaseOperacional,
                idMunicipio, idCentroCusto,
                idSupervisor, idCoordenador,
                idStatus,
                null,
                null,
                null,
                Set.of(UUID.randomUUID()),
                null
        );

        when(repository.save(any(Obra.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ObraResponse mockResponse = new ObraResponse(
                UUID.randomUUID(), "1228694", "REDE PEDAGOGICA REMANSO (CONCESSIONARIA)", TipoObra.OBRA,
                "Descricao", "9101923953", new BigDecimal("150000.00"),
                LocalDate.of(2025, 11, 3), LocalDate.of(2025, 11, 30), null,
                -9.439007, -41.968228,
                null, null, null, null, null, null, null, null,
                idStatus, "EM EXECUÇÃO", false,
                idSupervisor, "SUPERVISOR TESTE",
                idCoordenador, "COORDENADOR TESTE",
                null, null, null, null, null, null, null,
                request.equipes(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                true, OffsetDateTime.now(), null, "admin", null
        );

        when(mapper.toResponse(any(Obra.class), any(), any(), any(), any(), any(), any(), anyList())).thenReturn(mockResponse);

        ObraResponse response = useCases.criar(request);

        assertThat(response).isNotNull();
        assertThat(response.numero()).isEqualTo("1228694");
        assertThat(response.titulo()).isEqualTo("REDE PEDAGOGICA REMANSO (CONCESSIONARIA)");
        assertThat(response.supervisorId()).isEqualTo(idSupervisor);
        assertThat(response.coordenadorId()).isEqualTo(idCoordenador);
        verify(repository).save(argThat(obra -> obra.getTipoObra() == TipoObra.OBRA));
    }

    @Test
    @DisplayName("Deve recusar criação quando número já existir ativo")
    void deveRecusarCriacaoComNumeroDuplicado() {
        when(repository.existsByNumeroIgnoreCaseAndAtivoTrue("1228694")).thenReturn(true);

        ObraRequest request = new ObraRequest(
                "B-1228694", "TITULO", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30), null,
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, null, null, null, null
        );

        assertThatThrownBy(() -> useCases.criar(request))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Já existe uma obra ativa");
    }

    @Test
    @DisplayName("Deve recusar criação quando supervisor não existir ou não estiver ativo")
    void deveRecusarCriacaoComSupervisorInvalido() {
        when(repository.existsByNumeroIgnoreCaseAndAtivoTrue("1228694")).thenReturn(false);
        UUID idSupervisor = UUID.randomUUID();
        when(supervisorApi.existeSupervisor(idSupervisor)).thenReturn(false);

        ObraRequest request = new ObraRequest(
                "B-1228694", "TITULO", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30), null,
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                idSupervisor, UUID.randomUUID(),
                UUID.randomUUID(), null, null, null, null, null
        );

        assertThatThrownBy(() -> useCases.criar(request))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Supervisor informado não foi encontrado");
    }

    @Test
    @DisplayName("Deve recusar criação quando coordenador não existir ou não estiver ativo")
    void deveRecusarCriacaoComCoordenadorInvalido() {
        when(repository.existsByNumeroIgnoreCaseAndAtivoTrue("1228694")).thenReturn(false);
        UUID idSupervisor = UUID.randomUUID();
        UUID idCoordenador = UUID.randomUUID();
        when(supervisorApi.existeSupervisor(idSupervisor)).thenReturn(true);
        when(coordenadorApi.existeCoordenador(idCoordenador)).thenReturn(false);

        ObraRequest request = new ObraRequest(
                "B-1228694", "TITULO", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30), null,
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                idSupervisor, idCoordenador,
                UUID.randomUUID(), null, null, null, null, null
        );

        assertThatThrownBy(() -> useCases.criar(request))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Coordenador informado não foi encontrado");
    }

    @Test
    @DisplayName("Deve vincular subprojeto com sucesso")
    void deveVincularSubprojetoComSucesso() {
        UUID idPai = UUID.randomUUID();
        UUID idFilha = UUID.randomUUID();

        Obra pai = new Obra(
                "000001", "PROJETO PRINCIPAL", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30),
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new StatusObra("EM EXECUÇÃO", 1, false), null, null
        );

        Obra filha = new Obra(
                "1102881", "PROJETO MEDIDOR", null, null, null,
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 30),
                null, null,
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new StatusObra("EM EXECUÇÃO", 1, false), null, null
        );

        when(repository.findById(idPai)).thenReturn(Optional.of(pai));
        when(repository.findById(idFilha)).thenReturn(Optional.of(filha));
        when(repository.save(filha)).thenReturn(filha);

        SubprojetoVinculoRequest request = new SubprojetoVinculoRequest(idFilha, TipoVinculoSubprojeto.MEDIDOR);
        useCases.vincularSubprojeto(idPai, request);

        assertThat(filha.getObraPai()).isEqualTo(pai);
        assertThat(filha.getTipoVinculoPai()).isEqualTo(TipoVinculoSubprojeto.MEDIDOR);
        verify(relacionamentoProjetoUseCases).garantirVinculoLegado(
                eq(idPai),
                argThat(vinculo -> vinculo.idProjetoRelacionado().equals(idFilha)
                        && vinculo.tipoVinculo() == TipoVinculoProjeto.MEDIDOR)
        );
        verify(repository).save(filha);
    }
}
