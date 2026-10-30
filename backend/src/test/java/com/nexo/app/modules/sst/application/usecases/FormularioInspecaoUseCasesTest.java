package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.CriarFormularioRequest;
import com.nexo.app.modules.sst.application.dto.CriarFormularioRequest.GrupoRequest;
import com.nexo.app.modules.sst.application.dto.CriarFormularioRequest.OpcaoRequest;
import com.nexo.app.modules.sst.application.dto.CriarFormularioRequest.PerguntaRequest;
import com.nexo.app.modules.sst.application.dto.FormularioInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.ReordenarRequest;
import com.nexo.app.modules.sst.application.mappers.FormularioInspecaoMapper;
import com.nexo.app.modules.sst.domain.FormularioInspecao;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.GrupoPergunta;
import com.nexo.app.modules.sst.domain.TipoResposta;
import com.nexo.app.modules.sst.infrastructure.persistence.FormularioInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.ExecucaoChecklistGovernancaSstRepository;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormularioInspecaoUseCasesTest {

    @Mock
    private FormularioInspecaoRepository repository;
    @Mock
    private InspecaoSegurancaRepository inspecaoRepository;
    @Mock
    private ExecucaoChecklistGovernancaSstRepository checklistGovernancaRepository;
    @Mock
    private FormularioInspecaoMapper mapper;

    @InjectMocks
    private FormularioInspecaoUseCases useCases;

    private CriarFormularioRequest requestValido() {
        return new CriarFormularioRequest("Inspeção de Campo", "Checklist", List.of(
                new GrupoRequest("Kit SMC", List.of(
                        new PerguntaRequest("A câmera estava conservada?", TipoResposta.MULTIPLA_ESCOLHA, true, List.of(
                                new OpcaoRequest("Sim", false, null),
                                new OpcaoRequest("Não", true, GravidadeDesvio.ALTA)))))));
    }

    @Test
    @DisplayName("criar deve montar e persistir a árvore completa do formulário")
    void criarDevePersistirArvore() {
        when(repository.findByTituloIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(
                new FormularioInspecaoResponse(UUID.randomUUID(), "Inspeção de Campo", "Checklist", true, false, List.of()));

        useCases.criar(requestValido());

        ArgumentCaptor<FormularioInspecao> captor = ArgumentCaptor.forClass(FormularioInspecao.class);
        verify(repository).save(captor.capture());

        FormularioInspecao salvo = captor.getValue();
        assertEquals(1, salvo.getGrupos().size());
        assertEquals(1, salvo.getGrupos().get(0).getPerguntas().size());
        assertEquals(2, salvo.getGrupos().get(0).getPerguntas().get(0).getOpcoes().size());
        assertTrue(salvo.getGrupos().get(0).getPerguntas().get(0).getOpcoes().get(1).isGeraDesvio());
    }

    @Test
    @DisplayName("criar deve rejeitar estrutura inválida (pergunta objetiva sem opções)")
    void criarDeveRejeitarEstruturaInvalida() {
        CriarFormularioRequest invalido = new CriarFormularioRequest("F", "d", List.of(
                new GrupoRequest("G", List.of(
                        new PerguntaRequest("P", TipoResposta.MULTIPLA_ESCOLHA, true, List.of())))));
        when(repository.findByTituloIgnoreCase(any())).thenReturn(Optional.empty());

        assertThrows(InvarianteVioladaException.class, () -> useCases.criar(invalido));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("criar deve rejeitar opção que gera desvio sem gravidade definida")
    void criarDeveRejeitarOpcaoGeraDesvioSemGravidade() {
        CriarFormularioRequest invalido = new CriarFormularioRequest("F", "d", List.of(
                new GrupoRequest("G", List.of(
                        new PerguntaRequest("P", TipoResposta.MULTIPLA_ESCOLHA, true, List.of(
                                new OpcaoRequest("Não", true, null)))))));
        when(repository.findByTituloIgnoreCase(any())).thenReturn(Optional.empty());

        assertThrows(InvarianteVioladaException.class, () -> useCases.criar(invalido));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("atualizarEstrutura deve bloquear edição de formulário já utilizado em inspeções")
    void atualizarEstruturaDeveBloquearAposUso() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(new FormularioInspecao("F", "d")));
        when(inspecaoRepository.existsByIdFormulario(id)).thenReturn(true);

        assertThrows(InvarianteVioladaException.class, () -> useCases.atualizarEstrutura(id, requestValido()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("atualizarEstrutura deve bloquear edição após uso em checklist de governança")
    void atualizarEstruturaDeveBloquearAposUsoNaGovernanca() {
        UUID id = UUID.randomUUID();
        FormularioInspecao formulario = new FormularioInspecao("Inspeção de Campo", "Checklist");
        when(repository.findById(id)).thenReturn(Optional.of(formulario));
        when(inspecaoRepository.existsByIdFormulario(id)).thenReturn(false);
        when(checklistGovernancaRepository.existsByIdFormulario(id)).thenReturn(true);

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.atualizarEstrutura(id, requestValido()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("criar deve rejeitar título duplicado")
    void criarDeveRejeitarTituloDuplicado() {
        when(repository.findByTituloIgnoreCase(any()))
                .thenReturn(Optional.of(new FormularioInspecao("Inspeção de Campo", "x")));

        assertThrows(InvarianteVioladaException.class, () -> useCases.criar(requestValido()));
    }

    @Test
    @DisplayName("reordenar grupos deve ser permitido mesmo com o formulário já em uso")
    void reordenarGruposPermitidoComFormularioEmUso() {
        UUID idFormulario = UUID.randomUUID();
        FormularioInspecao form = new FormularioInspecao("F", "d");
        GrupoPergunta g1 = form.adicionarGrupo("A");
        GrupoPergunta g2 = form.adicionarGrupo("B");
        UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID();
        ReflectionTestUtils.setField(form, "id", idFormulario);
        ReflectionTestUtils.setField((BaseEntity) g1, "id", id1);
        ReflectionTestUtils.setField((BaseEntity) g2, "id", id2);

        when(repository.findById(idFormulario)).thenReturn(Optional.of(form));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inspecaoRepository.existsByIdFormulario(idFormulario)).thenReturn(true); // já usado
        when(mapper.toResponse(any())).thenReturn(
                new FormularioInspecaoResponse(idFormulario, "F", "d", true, true, List.of()));

        // Não deve lançar exceção de trava, e deve aplicar a nova ordem
        useCases.reordenarGrupos(idFormulario, new ReordenarRequest(List.of(id2, id1)));

        assertEquals(id2, form.getGrupos().get(0).getId());
        assertEquals(id1, form.getGrupos().get(1).getId());
        verify(repository).save(form);
    }

    @Test
    @DisplayName("criar e duplicar devem preservar a opção padrão")
    void criarEDuplicarPreservamOpcaoPadrao() {
        when(repository.findByTituloIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            FormularioInspecao f = inv.getArgument(0);
            return new FormularioInspecaoResponse(f.getId(), f.getTitulo(), f.getDescricao(), true, false, List.of());
        });

        CriarFormularioRequest req = new CriarFormularioRequest("Inspeção com Padrão", "Checklist", List.of(
                new GrupoRequest("Grupo 1", List.of(
                        new PerguntaRequest("EPI ok?", TipoResposta.MULTIPLA_ESCOLHA, true, List.of(
                                new OpcaoRequest("CONFORME", false, null, true),
                                new OpcaoRequest("NÃO CONFORME", true, GravidadeDesvio.MEDIA, false)))))));

        useCases.criar(req);

        ArgumentCaptor<FormularioInspecao> captor = ArgumentCaptor.forClass(FormularioInspecao.class);
        verify(repository, atLeastOnce()).save(captor.capture());

        FormularioInspecao salvo = captor.getValue();
        assertTrue(salvo.getGrupos().get(0).getPerguntas().get(0).getOpcoes().get(0).isPadrao());
        assertFalse(salvo.getGrupos().get(0).getPerguntas().get(0).getOpcoes().get(1).isPadrao());

        // Agora testando duplicar
        UUID idOriginal = UUID.randomUUID();
        ReflectionTestUtils.setField(salvo, "id", idOriginal);
        when(repository.findById(idOriginal)).thenReturn(Optional.of(salvo));

        useCases.duplicar(idOriginal);
        verify(repository, atLeast(2)).save(captor.capture());

        FormularioInspecao copia = captor.getValue();
        assertTrue(copia.getGrupos().get(0).getPerguntas().get(0).getOpcoes().get(0).isPadrao());
    }
}
