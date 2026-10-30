package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.modules.sst.application.dto.AtividadeSstRequest;
import com.nexo.app.modules.sst.application.dto.AtividadeSstResponse;
import com.nexo.app.modules.sst.application.mappers.AtividadeSstMapper;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AtividadeSstRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
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
class AtividadeSstUseCasesTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID GESTOR = UUID.randomUUID();
    private static final UUID PROCESSO = UUID.randomUUID();

    @Mock
    private AtividadeSstRepository repository;
    @Mock
    private AtividadeSstMapper mapper;
    @Mock
    private ColaboradorApi colaboradorApi;
    @Mock
    private ProcessoNegocioApi processoNegocioApi;

    @InjectMocks
    private AtividadeSstUseCases useCases;

    @BeforeEach
    void configurarPadroes() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> respostaDe(inv.getArgument(0)));
        when(colaboradorApi.existeColaborador(GESTOR)).thenReturn(true);
        when(colaboradorApi.buscarResumosPorIds(anyCollection())).thenReturn(
                Map.of(GESTOR, new ResumoColaborador(GESTOR, "MARIA SOUZA", "TÉCNICA DE SEGURANÇA", "BASE SUL", true)));
        when(processoNegocioApi.existeProcesso(PROCESSO)).thenReturn(true);
        when(processoNegocioApi.buscarPorIdResponse(PROCESSO))
                .thenReturn(Optional.of(new ProcessoNegocioResponse(PROCESSO, "MANUTENÇÃO", true)));
    }

    private AtividadeSstResponse respostaDe(AtividadeSst atividade) {
        return new AtividadeSstResponse(atividade.getId(), atividade.getNome(), atividade.getDescricao(),
                atividade.getIdGestor(), null, null, atividade.getIdProcesso(), null, atividade.isAtivo());
    }

    @Test
    @DisplayName("criar deve persistir a atividade e resolver o gestor na resposta")
    void criarDevePersistirEHidratar() {
        AtividadeSstResponse response = useCases.criar(
                new AtividadeSstRequest("Detonação", "Uso de explosivos", GESTOR, PROCESSO));

        ArgumentCaptor<AtividadeSst> captor = ArgumentCaptor.forClass(AtividadeSst.class);
        verify(repository).save(captor.capture());

        assertEquals("detonacao", captor.getValue().getNomeNormalizado());
        assertEquals(GESTOR, captor.getValue().getIdGestor());
        assertEquals("MARIA SOUZA", response.nomeGestor());
        assertEquals("TÉCNICA DE SEGURANÇA", response.funcaoGestor());
        assertEquals("MANUTENÇÃO", response.nomeProcesso());
    }

    @Test
    @DisplayName("criar deve aceitar atividade sem gestor — a carga inicial nasce assim")
    void criarDeveAceitarSemGestor() {
        AtividadeSstResponse response = useCases.criar(
                new AtividadeSstRequest("Trajeto", null, null, null));

        assertNull(response.idGestor());
        assertNull(response.nomeGestor());
        verify(colaboradorApi, never()).existeColaborador(any());
    }

    @Test
    @DisplayName("criar deve recusar gestor inexistente ou inativo")
    void criarDeveRecusarGestorInvalido() {
        UUID desligado = UUID.randomUUID();
        when(colaboradorApi.existeColaborador(desligado)).thenReturn(false);

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.criar(new AtividadeSstRequest("Poda", null, desligado, null)));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("criar deve recusar processo inexistente")
    void criarDeveRecusarProcessoInvalido() {
        UUID inexistente = UUID.randomUUID();
        when(processoNegocioApi.existeProcesso(inexistente)).thenReturn(false);

        assertThrows(RecursoNaoEncontradoException.class,
                () -> useCases.criar(new AtividadeSstRequest("Poda", null, GESTOR, inexistente)));
    }

    @Test
    @DisplayName("criar deve recusar nome duplicado ignorando acento e caixa")
    void criarDeveRecusarNomeDuplicado() {
        when(repository.existsByNomeNormalizadoAndAtivoTrue("detonacao")).thenReturn(true);

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.criar(new AtividadeSstRequest("DETONAÇÃO", null, GESTOR, null)));
    }

    @Test
    @DisplayName("atualizar deve trocar o gestor sem tocar em eventos passados")
    void atualizarDeveTrocarGestor() {
        AtividadeSst atividade = new AtividadeSst("Poda", null, UUID.randomUUID(), null);
        when(repository.findById(ID)).thenReturn(Optional.of(atividade));

        useCases.atualizar(ID, new AtividadeSstRequest("Poda", null, GESTOR, null));

        assertEquals(GESTOR, atividade.getIdGestor());
        verify(repository).existsByNomeNormalizadoAndAtivoTrueAndIdNot("poda", ID);
    }

    @Test
    @DisplayName("desativar deve aplicar soft delete, sem apagar a linha")
    void desativarDeveAplicarSoftDelete() {
        AtividadeSst atividade = new AtividadeSst("Varredura", null, GESTOR, null);
        when(repository.findById(ID)).thenReturn(Optional.of(atividade));

        useCases.desativar(ID);

        assertFalse(atividade.isAtivo());
        verify(repository).save(atividade);
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("ativar deve recusar quando o nome foi tomado durante a desativação")
    void ativarDeveRecusarNomeTomado() {
        AtividadeSst atividade = new AtividadeSst("Varredura", null, GESTOR, null);
        atividade.desativar();
        when(repository.findById(ID)).thenReturn(Optional.of(atividade));
        when(repository.existsByNomeNormalizadoAndAtivoTrueAndIdNot("varredura", ID)).thenReturn(true);

        assertThrows(InvarianteVioladaException.class, () -> useCases.ativar(ID));

        assertFalse(atividade.isAtivo());
    }

    @Test
    @DisplayName("listarOpcoes deve resolver os gestores da lista em uma consulta só")
    void listarOpcoesDeveResolverGestoresEmLote() {
        when(repository.findByAtivoTrueOrderByNomeAsc()).thenReturn(List.of(
                new AtividadeSst("Detonação", null, GESTOR, null),
                new AtividadeSst("Poda", null, GESTOR, null),
                new AtividadeSst("Trajeto", null, null, null)));

        List<AtividadeSstResponse> opcoes = useCases.listarOpcoes();

        assertEquals(3, opcoes.size());
        assertEquals("MARIA SOUZA", opcoes.getFirst().nomeGestor());
        assertNull(opcoes.getLast().nomeGestor(), "Atividade sem gestor não inventa nome");
        verify(colaboradorApi, times(1)).buscarResumosPorIds(anyCollection());
    }

    @Test
    @DisplayName("buscarPorId deve falhar quando a atividade não existe")
    void buscarPorIdDeveFalharQuandoNaoExiste() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.buscarPorId(ID));
    }
}
