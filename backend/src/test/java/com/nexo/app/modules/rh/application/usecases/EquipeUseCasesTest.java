package com.nexo.app.modules.rh.application.usecases;

import com.nexo.app.modules.contrato.ContratoApi;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.application.dto.EquipeMembroRequest;
import com.nexo.app.modules.rh.application.dto.EquipeRequest;
import com.nexo.app.modules.rh.application.mappers.EquipeMapper;
import com.nexo.app.modules.rh.domain.*;
import com.nexo.app.modules.rh.infrastructure.persistence.*;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.domain.events.EquipeCriadaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipeUseCasesTest {

    @Mock
    private EquipeRepository repository;
    @Mock
    private TipoEquipeRepository tipoEquipeRepository;
    @Mock
    private VeiculoRepository veiculoRepository;
    @Mock
    private ColaboradorRepository colaboradorRepository;
    @Mock
    private CoordenadorRepository coordenadorRepository;
    @Mock
    private SupervisorRepository supervisorRepository;
    @Mock
    private BaseOperacionalRepository baseOperacionalRepository;
    @Mock
    private ProcessoNegocioRepository processoRepository;
    @Mock
    private EquipeMapper mapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private MembroEquipeUseCase membroEquipeUseCase;
    @Mock
    private ContratoApi contratoApi;
    @Mock
    private MetaFaturamentoUseCases metaFaturamentoUseCases;

    @InjectMocks
    private EquipeUseCases equipeUseCases;

    private UUID idTipoEquipe;
    private UUID idBaseOperacional;
    private UUID idVeiculo;
    private UUID idCoordenador;
    private UUID idSupervisor;
    private UUID idProcesso;
    private UUID idColaboradorMembro;

    private TipoEquipe tipoEquipe;
    private CentroFaturamento centroFaturamento;
    private BaseOperacional baseOperacional;
    private Veiculo veiculo;
    private ProcessoNegocio processo;
    private Funcao funcao;
    private Colaborador colabCoord;
    private Colaborador colabSup;
    private Colaborador colabMembro;
    private Coordenador coordenador;
    private Supervisor supervisor;

    @BeforeEach
    void setUp() {
        lenient().when(metaFaturamentoUseCases.comMetaVigente(any(EquipeResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        idTipoEquipe = UUID.randomUUID();
        idBaseOperacional = UUID.randomUUID();
        idVeiculo = UUID.randomUUID();
        idCoordenador = UUID.randomUUID();
        idSupervisor = UUID.randomUUID();
        idProcesso = UUID.randomUUID();
        idColaboradorMembro = UUID.randomUUID();

        processo = new ProcessoNegocio("COMERCIAL");
        ReflectionTestUtils.setField(processo, "id", idProcesso);

        centroFaturamento = new CentroFaturamento("COMERCIAL");
        ReflectionTestUtils.setField(centroFaturamento, "id", UUID.randomUUID());

        baseOperacional = new BaseOperacional("BASE SUL", "BA", "Rua Central, 100", centroFaturamento);
        ReflectionTestUtils.setField(baseOperacional, "id", idBaseOperacional);

        tipoEquipe = new TipoEquipe("LINHA VIVA");
        ReflectionTestUtils.setField(tipoEquipe, "id", idTipoEquipe);

        veiculo = new Veiculo("ABC1234", "Caminhão", "Ford", baseOperacional);
        ReflectionTestUtils.setField(veiculo, "id", idVeiculo);

        funcao = new Funcao("ELETRICISTA", "Eletricista");
        ReflectionTestUtils.setField(funcao, "id", UUID.randomUUID());

        colabCoord = new Colaborador("Carlos", "Silva", "Carlos Silva", "111.222.333-44", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(colabCoord, "id", UUID.randomUUID());

        colabSup = new Colaborador("Marcos", "Oliveira", "Marcos Oliveira", "222.333.444-55", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(colabSup, "id", UUID.randomUUID());

        colabMembro = new Colaborador("Joao", "Santos", "Joao Santos", "333.444.555-66", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(colabMembro, "id", idColaboradorMembro);
        ReflectionTestUtils.setField(colabMembro, "idUsuario", UUID.randomUUID());

        coordenador = new Coordenador(colabCoord, LocalDate.now(), null);
        ReflectionTestUtils.setField(coordenador, "id", idCoordenador);

        supervisor = new Supervisor(colabSup, coordenador, LocalDate.now(), null);
        ReflectionTestUtils.setField(supervisor, "id", idSupervisor);
    }

    @Test
    @DisplayName("Deve criar equipe com sucesso buscando Coordenador e Supervisor pelos repositórios")
    void deveCriarEquipeComSucesso() {
        EquipeRequest request = new EquipeRequest(
                "EQ-01",
                idTipoEquipe,
                idBaseOperacional,
                idVeiculo,
                idCoordenador,
                idSupervisor,
                idProcesso,
                null,
                List.of(new EquipeMembroRequest(idColaboradorMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL))
        );

        when(repository.existsByCodigoIgnoreCase("EQ-01")).thenReturn(false);
        when(tipoEquipeRepository.findById(idTipoEquipe)).thenReturn(Optional.of(tipoEquipe));
        when(baseOperacionalRepository.findById(idBaseOperacional)).thenReturn(Optional.of(baseOperacional));
        when(veiculoRepository.findById(idVeiculo)).thenReturn(Optional.of(veiculo));
        when(coordenadorRepository.buscarPorIdComDetalhes(idCoordenador)).thenReturn(Optional.of(coordenador));
        when(supervisorRepository.buscarPorIdComDetalhes(idSupervisor)).thenReturn(Optional.of(supervisor));
        when(processoRepository.findById(idProcesso)).thenReturn(Optional.of(processo));
        when(colaboradorRepository.findById(idColaboradorMembro)).thenReturn(Optional.of(colabMembro));
        when(repository.save(any(Equipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EquipeResponse expectedResponse = new EquipeResponse(
                UUID.randomUUID(), "EQ-01", null, null, null, null, null, null, null, true, List.of(), null
        );
        when(mapper.toResponse(any(Equipe.class))).thenReturn(expectedResponse);

        EquipeResponse response = equipeUseCases.criar(request);

        assertThat(response).isNotNull();
        verify(coordenadorRepository).buscarPorIdComDetalhes(idCoordenador);
        verify(supervisorRepository).buscarPorIdComDetalhes(idSupervisor);
        verify(repository).save(any(Equipe.class));
        verify(eventPublisher).publishEvent(any(EquipeCriadaEvent.class));
    }

    @Test
    @DisplayName("Deve falhar ao criar equipe quando coordenador não for encontrado")
    void deveFalharQuandoCoordenadorNaoEncontrado() {
        EquipeRequest request = new EquipeRequest(
                "EQ-01",
                idTipoEquipe,
                idBaseOperacional,
                idVeiculo,
                idCoordenador,
                idSupervisor,
                idProcesso,
                null,
                List.of(new EquipeMembroRequest(idColaboradorMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL))
        );

        when(repository.existsByCodigoIgnoreCase("EQ-01")).thenReturn(false);
        when(tipoEquipeRepository.findById(idTipoEquipe)).thenReturn(Optional.of(tipoEquipe));
        when(baseOperacionalRepository.findById(idBaseOperacional)).thenReturn(Optional.of(baseOperacional));
        when(veiculoRepository.findById(idVeiculo)).thenReturn(Optional.of(veiculo));
        when(coordenadorRepository.buscarPorIdComDetalhes(idCoordenador)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> equipeUseCases.criar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Coordenador não encontrado");
    }

    @Test
    @DisplayName("Deve falhar ao criar equipe quando supervisor não for encontrado")
    void deveFalharQuandoSupervisorNaoEncontrado() {
        EquipeRequest request = new EquipeRequest(
                "EQ-01",
                idTipoEquipe,
                idBaseOperacional,
                idVeiculo,
                idCoordenador,
                idSupervisor,
                idProcesso,
                null,
                List.of(new EquipeMembroRequest(idColaboradorMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL))
        );

        when(repository.existsByCodigoIgnoreCase("EQ-01")).thenReturn(false);
        when(tipoEquipeRepository.findById(idTipoEquipe)).thenReturn(Optional.of(tipoEquipe));
        when(baseOperacionalRepository.findById(idBaseOperacional)).thenReturn(Optional.of(baseOperacional));
        when(veiculoRepository.findById(idVeiculo)).thenReturn(Optional.of(veiculo));
        when(coordenadorRepository.buscarPorIdComDetalhes(idCoordenador)).thenReturn(Optional.of(coordenador));
        when(supervisorRepository.buscarPorIdComDetalhes(idSupervisor)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> equipeUseCases.criar(request))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Supervisor não encontrado");
    }

    @Test
    @DisplayName("Deve transferir a responsabilidade ao atualizar a equipe com novo responsável")
    void deveTransferirResponsabilidadeAoAtualizar() {
        Colaborador colabParceiro = novoColaboradorComUsuario("Guilherme");
        Equipe equipe = equipeExistente();
        equipe.alocarMembro(colabMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL);
        equipe.alocarMembro(colabParceiro, EquipeMembro.FuncaoMembro.PARCEIRO);
        prepararAtualizacao(equipe);
        when(colaboradorRepository.findById(colabParceiro.getId())).thenReturn(Optional.of(colabParceiro));

        equipeUseCases.atualizar(equipe.getId(), requestAtualizacao(List.of(
                new EquipeMembroRequest(idColaboradorMembro, EquipeMembro.FuncaoMembro.PARCEIRO),
                new EquipeMembroRequest(colabParceiro.getId(), EquipeMembro.FuncaoMembro.RESPONSAVEL))));

        assertThat(funcaoAtiva(equipe, colabParceiro)).isEqualTo(EquipeMembro.FuncaoMembro.RESPONSAVEL);
        assertThat(funcaoAtiva(equipe, colabMembro)).isEqualTo(EquipeMembro.FuncaoMembro.PARCEIRO);
        assertThat(equipe.getIdUsuario()).isEqualTo(colabParceiro.getIdUsuario());
    }

    @Test
    @DisplayName("Deve encerrar membro removido e alocar membro novo ao atualizar a equipe")
    void deveSincronizarMembrosAoAtualizar() {
        Colaborador colabRemovido = novoColaboradorComUsuario("Removido");
        Colaborador colabNovo = novoColaboradorComUsuario("Novo");
        Equipe equipe = equipeExistente();
        equipe.alocarMembro(colabMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL);
        equipe.alocarMembro(colabRemovido, EquipeMembro.FuncaoMembro.PARCEIRO);
        prepararAtualizacao(equipe);
        when(colaboradorRepository.findById(colabNovo.getId())).thenReturn(Optional.of(colabNovo));
        doAnswer(invocation -> {
            Equipe destino = invocation.getArgument(0);
            destino.alocarMembro(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(membroEquipeUseCase).processarAlocacaoUnica(any(Equipe.class), any(Colaborador.class), any());

        equipeUseCases.atualizar(equipe.getId(), requestAtualizacao(List.of(
                new EquipeMembroRequest(idColaboradorMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL),
                new EquipeMembroRequest(colabNovo.getId(), EquipeMembro.FuncaoMembro.PARCEIRO))));

        assertThat(funcaoAtiva(equipe, colabRemovido)).isNull();
        assertThat(funcaoAtiva(equipe, colabNovo)).isEqualTo(EquipeMembro.FuncaoMembro.PARCEIRO);
        assertThat(funcaoAtiva(equipe, colabMembro)).isEqualTo(EquipeMembro.FuncaoMembro.RESPONSAVEL);
    }

    @Test
    @DisplayName("Deve rejeitar atualização de membros sem exatamente um responsável")
    void deveRejeitarAtualizacaoSemResponsavel() {
        Equipe equipe = equipeExistente();
        equipe.alocarMembro(colabMembro, EquipeMembro.FuncaoMembro.RESPONSAVEL);
        prepararAtualizacao(equipe);

        assertThatThrownBy(() -> equipeUseCases.atualizar(equipe.getId(), requestAtualizacao(List.of(
                new EquipeMembroRequest(idColaboradorMembro, EquipeMembro.FuncaoMembro.PARCEIRO)))))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("exatamente um responsável");
    }

    private Equipe equipeExistente() {
        Equipe equipe = new Equipe("EQ-01", tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo);
        ReflectionTestUtils.setField(equipe, "id", UUID.randomUUID());
        return equipe;
    }

    private Colaborador novoColaboradorComUsuario(String nome) {
        Colaborador colaborador = new Colaborador(nome, "Teste", nome + " Teste", "444.555.666-77", processo, baseOperacional, funcao, LocalDate.now());
        ReflectionTestUtils.setField(colaborador, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(colaborador, "idUsuario", UUID.randomUUID());
        return colaborador;
    }

    private void prepararAtualizacao(Equipe equipe) {
        when(repository.findById(equipe.getId())).thenReturn(Optional.of(equipe));
        when(tipoEquipeRepository.findById(idTipoEquipe)).thenReturn(Optional.of(tipoEquipe));
        when(baseOperacionalRepository.findById(idBaseOperacional)).thenReturn(Optional.of(baseOperacional));
        when(veiculoRepository.findById(idVeiculo)).thenReturn(Optional.of(veiculo));
        when(coordenadorRepository.buscarPorIdComDetalhes(idCoordenador)).thenReturn(Optional.of(coordenador));
        when(supervisorRepository.buscarPorIdComDetalhes(idSupervisor)).thenReturn(Optional.of(supervisor));
        when(processoRepository.findById(idProcesso)).thenReturn(Optional.of(processo));
        lenient().when(colaboradorRepository.findById(idColaboradorMembro)).thenReturn(Optional.of(colabMembro));
        lenient().when(repository.save(any(Equipe.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private EquipeRequest requestAtualizacao(List<EquipeMembroRequest> membros) {
        return new EquipeRequest("EQ-01", idTipoEquipe, idBaseOperacional, idVeiculo, idCoordenador,
                idSupervisor, idProcesso, null, membros);
    }

    private EquipeMembro.FuncaoMembro funcaoAtiva(Equipe equipe, Colaborador colaborador) {
        return equipe.getMembros().stream()
                .filter(EquipeMembro::isVinculoAtivo)
                .filter(m -> m.getColaborador().getId().equals(colaborador.getId()))
                .map(EquipeMembro::getFuncao)
                .findFirst()
                .orElse(null);
    }
}
