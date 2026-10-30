package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.sst.application.dto.DesvioDetalhadoResponse;
import com.nexo.app.modules.sst.application.dto.DesvioSegurancaFilter;
import com.nexo.app.modules.sst.domain.*;
import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DesvioSegurancaUseCasesTest {

    @Mock private DesvioSegurancaRepository repository;
    @Mock private FormularioInspecaoRepository formularioRepository;
    @Mock private ProcessoNegocioApi processoApi;
    @Mock private BaseOperacionalApi baseOperacionalApi;
    @Mock private EquipeApi equipeApi;
    @Mock private VeiculoApi veiculoApi;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks
    private DesvioSegurancaUseCases useCases;

    private final UUID idFormulario = UUID.randomUUID();
    private final UUID idPergunta = UUID.randomUUID();
    private final UUID idOpcaoNao = UUID.randomUUID();
    private final UUID idInspetor = UUID.randomUUID();
    private final UUID idColaborador = UUID.randomUUID();

    private final DesvioSegurancaFilter semFiltro = new DesvioSegurancaFilter(
            null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null);

    @BeforeEach
    void stubsComuns() {
        when(formularioRepository.findAllById(anyCollection())).thenReturn(List.of(formulario()));
        when(colaboradorApi.buscarResumosPorIds(anyCollection())).thenReturn(Map.of(
                idColaborador, new ResumoColaborador(idColaborador, "MARIA SOUZA", "ELETRICISTA", "BASE SUL", true)));
        when(colaboradorApi.buscarNomeCompletoPorUsuario(idInspetor)).thenReturn(Optional.of("JOAO INSPETOR"));
        when(usuarioApi.buscarLoginsPorIds(anyCollection())).thenReturn(Map.of(idInspetor, "JINSPETOR"));
        when(processoApi.buscarPorIdResponse(any())).thenReturn(Optional.empty());
        when(baseOperacionalApi.buscarPorIdResponse(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("A linha da listagem se explica sozinha: item do formulário, pessoas e atraso")
    void deveHidratarDesvioComContextoDaInspecaoDeOrigem() {
        DesvioSeguranca desvio = novoDesvio(LocalDate.now().minusDays(3), StatusDesvio.ABERTO);
        paginaCom(List.of(desvio));

        DesvioDetalhadoResponse item = useCases.listar(semFiltro, PageRequest.of(0, 20)).getContent().getFirst();

        assertEquals("Inspeção de Campo", item.formulario());
        assertEquals("Kit SMC", item.grupo());
        assertEquals("A câmera estava conservada?", item.pergunta());
        assertEquals("Não", item.opcaoResposta());
        assertEquals("JOAO INSPETOR", item.inspetor());
        assertEquals(List.of("MARIA SOUZA"), item.colaboradores().stream().map(ResumoColaborador::nomeCompleto).toList());
        assertTrue(item.vencido());
        assertEquals(3, item.diasEmAtraso().intValue());
    }

    @Test
    @DisplayName("Desvio resolvido não conta como vencido, mesmo com o prazo estourado")
    void naoDeveMarcarComoVencidoDesvioJaResolvido() {
        DesvioSeguranca desvio = novoDesvio(LocalDate.now().minusDays(10), StatusDesvio.RESOLVIDO);
        paginaCom(List.of(desvio));

        DesvioDetalhadoResponse item = useCases.listar(semFiltro, PageRequest.of(0, 20)).getContent().getFirst();

        assertFalse(item.vencido());
        assertNull(item.diasEmAtraso());
    }

    @Test
    @DisplayName("Uma página inteira custa uma resolução por cadastro, não uma por linha")
    void deveResolverCadastrosUmaVezPorPagina() {
        InspecaoSeguranca inspecao = novaInspecao();
        paginaCom(List.of(
                registrarDesvio(inspecao, "Sem capacete"),
                registrarDesvio(inspecao, "Sem luva")));

        assertEquals(2, useCases.listar(semFiltro, PageRequest.of(0, 20)).getContent().size());

        verify(processoApi, times(1)).buscarPorIdResponse(any());
        verify(baseOperacionalApi, times(1)).buscarPorIdResponse(any());
        verify(colaboradorApi, times(1)).buscarResumosPorIds(anyCollection());
        verify(colaboradorApi, times(1)).buscarNomeCompletoPorUsuario(any());
        verify(formularioRepository, times(1)).findAllById(anyCollection());
    }

    @Test
    @DisplayName("Inspetor sem cadastro de colaborador aparece pelo login, não como linha vazia")
    void deveUsarLoginQuandoInspetorNaoTemColaboradorVinculado() {
        when(colaboradorApi.buscarNomeCompletoPorUsuario(idInspetor)).thenReturn(Optional.empty());
        paginaCom(List.of(novoDesvio(null, StatusDesvio.ABERTO)));

        DesvioDetalhadoResponse item = useCases.listar(semFiltro, PageRequest.of(0, 20)).getContent().getFirst();

        assertEquals("JINSPETOR", item.inspetor());
    }

    @Test
    @DisplayName("Sem sort explícito a paginação não fica à mercê da ordem do banco")
    void deveAplicarOrdenacaoPadraoQuandoNenhumaFoiPedida() {
        paginaCom(List.of());

        useCases.listar(semFiltro, PageRequest.of(0, 20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "dataCriacao"), captor.getValue().getSort());
    }

    @Test
    void deveManterOrdenacaoPedidaPeloCliente() {
        paginaCom(List.of());
        Sort porPrazo = Sort.by(Sort.Direction.ASC, "prazo");

        useCases.listar(semFiltro, PageRequest.of(0, 20, porPrazo));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), captor.capture());
        assertEquals(porPrazo, captor.getValue().getSort());
    }

    @Test
    void deveRecusarDetalheDeDesvioInexistente() {
        when(repository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.buscarPorId(UUID.randomUUID()));
    }

    // --------------------------------------------------------------------- //

    private void paginaCom(List<DesvioSeguranca> desvios) {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocacao -> new PageImpl<>(desvios, invocacao.getArgument(1), desvios.size()));
    }

    private DesvioSeguranca novoDesvio(LocalDate prazo, StatusDesvio status) {
        DesvioSeguranca desvio = registrarDesvio(novaInspecao(), "Sem capacete");
        ReflectionTestUtils.setField(desvio, "prazo", prazo);
        ReflectionTestUtils.setField(desvio, "status", status);
        return desvio;
    }

    private InspecaoSeguranca novaInspecao() {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                idFormulario, UUID.randomUUID(), UUID.randomUUID(), idInspetor,
                null, null, UUID.randomUUID(), OffsetDateTime.now(), null);
        inspecao.adicionarColaboradorInspecionado(idColaborador);
        setId(inspecao, UUID.randomUUID());
        return inspecao;
    }

    private DesvioSeguranca registrarDesvio(InspecaoSeguranca inspecao, String descricao) {
        DesvioSeguranca desvio = inspecao.registrarDesvio(
                idPergunta, idOpcaoNao, GravidadeDesvio.CRITICA, descricao, null, null, List.of(idColaborador));
        setId(desvio, UUID.randomUUID());
        return desvio;
    }

    private FormularioInspecao formulario() {
        FormularioInspecao form = new FormularioInspecao("Inspeção de Campo", "Checklist");
        GrupoPergunta grupo = form.adicionarGrupo("Kit SMC");
        Pergunta pergunta = grupo.adicionarPergunta("A câmera estava conservada?", TipoResposta.MULTIPLA_ESCOLHA, true);
        OpcaoResposta sim = pergunta.adicionarOpcao("Sim", false, null);
        OpcaoResposta nao = pergunta.adicionarOpcao("Não", true, GravidadeDesvio.CRITICA);
        setId(form, idFormulario);
        setId(grupo, UUID.randomUUID());
        setId(pergunta, idPergunta);
        setId(sim, UUID.randomUUID());
        setId(nao, idOpcaoNao);
        return form;
    }

    private void setId(BaseEntity entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
    }
}
