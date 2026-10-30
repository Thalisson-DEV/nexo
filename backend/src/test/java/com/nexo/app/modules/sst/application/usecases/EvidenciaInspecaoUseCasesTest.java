package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.EvidenciaDownload;
import com.nexo.app.modules.sst.application.dto.EvidenciaInspecaoResponse;
import com.nexo.app.modules.sst.domain.EvidenciaInspecao;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.infrastructure.persistence.EvidenciaInspecaoRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.shared.domain.exception.ArmazenamentoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvidenciaInspecaoUseCasesTest {

    @Mock private EvidenciaInspecaoRepository evidenciaRepository;
    @Mock private InspecaoSegurancaRepository inspecaoRepository;
    @Mock private ObjectStorageService objectStorage;

    @InjectMocks
    private EvidenciaInspecaoUseCases useCases;

    private final UUID idInspecao = UUID.randomUUID();
    private final UUID idEvidencia = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();

    private EvidenciaInspecao evidencia(UUID idUsuarioEnvio, String objectKey) {
        EvidenciaInspecao evidencia = new EvidenciaInspecao(idUsuarioEnvio, "foto.jpg", "image/jpeg", 3, objectKey);
        ReflectionTestUtils.setField(evidencia, "id", idEvidencia);
        return evidencia;
    }

    private InspecaoSeguranca inspecaoComEvidencias(int quantidade) {
        InspecaoSeguranca inspecao = new InspecaoSeguranca(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), usuario, null, null,
                UUID.randomUUID(), OffsetDateTime.now(), "obs");
        ReflectionTestUtils.setField(inspecao, "id", idInspecao);
        for (int i = 0; i < quantidade; i++) {
            EvidenciaInspecao e = new EvidenciaInspecao(
                    usuario, "foto.jpg", "image/jpeg", 3, "sst/inspecoes/evidencias/" + i + ".jpg");
            ReflectionTestUtils.setField(e, "id", i == 0 ? idEvidencia : UUID.randomUUID());
            inspecao.vincularEvidencia(e);
        }
        return inspecao;
    }

    @Test
    @DisplayName("enviar deve subir a foto ao R2 e devolver o ID a ser usado no lançamento")
    void enviarComSucesso() {
        when(evidenciaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "campo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        EvidenciaInspecaoResponse resp = useCases.enviar(arquivo, usuario);

        verify(objectStorage).enviar(anyString(), any(InputStream.class), eq(3L), eq("image/jpeg"));
        assertEquals("campo.jpg", resp.nomeArquivo());
        assertEquals("image/jpeg", resp.contentType());
        assertEquals(3L, resp.tamanhoBytes());
    }

    @Test
    @DisplayName("enviar deve recusar documento: a evidência da inspeção tem que ser fotografia")
    void enviarDocumentoRecusado() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "laudo.pdf", "application/pdf", new byte[]{1});

        assertThrows(InvarianteVioladaException.class, () -> useCases.enviar(arquivo, usuario));
        verify(objectStorage, never()).enviar(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("enviar deve recusar arquivo acima de 15 MB sem tocar no R2")
    void enviarArquivoGrande() {
        MultipartFile grande = mock(MultipartFile.class);
        when(grande.isEmpty()).thenReturn(false);
        when(grande.getContentType()).thenReturn("image/jpeg");
        when(grande.getSize()).thenReturn(15L * 1024 * 1024 + 1);

        assertThrows(InvarianteVioladaException.class, () -> useCases.enviar(grande, usuario));
        verify(objectStorage, never()).enviar(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("anexar deve acrescentar outra foto a uma inspeção já lançada")
    void anexarComSucesso() {
        InspecaoSeguranca inspecao = inspecaoComEvidencias(1);
        when(inspecaoRepository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));
        when(inspecaoRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "extra.png", "image/png", new byte[]{7});

        useCases.anexar(idInspecao, arquivo, usuario);

        assertEquals(2, inspecao.getEvidencias().size());
        verify(objectStorage).enviar(anyString(), any(InputStream.class), eq(1L), eq("image/png"));
    }

    @Test
    @DisplayName("baixar deve recuperar o conteúdo do R2 com os metadados da evidência")
    void baixarComSucesso() {
        InspecaoSeguranca inspecao = inspecaoComEvidencias(1);
        when(inspecaoRepository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));
        InputStream conteudo = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(objectStorage.baixar(anyString())).thenReturn(new StorageObject(conteudo, "image/jpeg", 3));

        EvidenciaDownload download = useCases.baixar(idInspecao, idEvidencia);

        assertEquals("image/jpeg", download.contentType());
        assertEquals("foto.jpg", download.nomeArquivo());
        assertEquals(conteudo, download.conteudo());
    }

    @Test
    void baixarNaoAcessaEvidenciaDeInspecaoDesativada() {
        when(inspecaoRepository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> useCases.baixar(idInspecao, idEvidencia));
        verify(objectStorage, never()).baixar(anyString());
    }

    @Test
    @DisplayName("remover deve recusar a última evidência da inspeção")
    void removerUltimaEvidencia() {
        InspecaoSeguranca inspecao = inspecaoComEvidencias(1);
        when(inspecaoRepository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));

        assertThrows(InvarianteVioladaException.class, () -> useCases.remover(idInspecao, idEvidencia));
        assertEquals(1, inspecao.getEvidencias().size());
        verify(objectStorage, never()).remover(anyString());
    }

    @Test
    @DisplayName("remover deve excluir a evidência e o objeto quando há outra que comprove a inspeção")
    void removerComOutraEvidencia() {
        InspecaoSeguranca inspecao = inspecaoComEvidencias(2);
        when(inspecaoRepository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));
        when(inspecaoRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        useCases.remover(idInspecao, idEvidencia);

        assertEquals(1, inspecao.getEvidencias().size());
        verify(objectStorage).remover("sst/inspecoes/evidencias/0.jpg");
    }

    @Test
    @DisplayName("remover deve concluir mesmo se a exclusão no R2 falhar (best-effort)")
    void removerToleranteAFalhaNoR2() {
        InspecaoSeguranca inspecao = inspecaoComEvidencias(2);
        when(inspecaoRepository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(inspecao));
        when(inspecaoRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ArmazenamentoException("indisponível")).when(objectStorage).remover(anyString());

        useCases.remover(idInspecao, idEvidencia); // não deve lançar

        assertEquals(1, inspecao.getEvidencias().size());
    }

    @Test
    @DisplayName("descartar deve apagar a foto pendente de quem a enviou")
    void descartarComSucesso() {
        EvidenciaInspecao pendente = evidencia(usuario, "sst/inspecoes/evidencias/abc.jpg");
        when(evidenciaRepository.findById(idEvidencia)).thenReturn(Optional.of(pendente));

        useCases.descartar(idEvidencia, usuario);

        verify(evidenciaRepository).delete(pendente);
        verify(objectStorage).remover("sst/inspecoes/evidencias/abc.jpg");
    }

    @Test
    @DisplayName("descartar deve recusar foto enviada por outro usuário")
    void descartarDeOutroUsuario() {
        when(evidenciaRepository.findById(idEvidencia))
                .thenReturn(Optional.of(evidencia(UUID.randomUUID(), "sst/inspecoes/evidencias/abc.jpg")));

        assertThrows(InvarianteVioladaException.class, () -> useCases.descartar(idEvidencia, usuario));
        verify(evidenciaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("descartar deve recusar foto já vinculada a uma inspeção")
    void descartarJaVinculada() {
        InspecaoSeguranca inspecao = inspecaoComEvidencias(1);
        when(evidenciaRepository.findById(idEvidencia)).thenReturn(Optional.of(inspecao.getEvidencias().get(0)));

        assertThrows(InvarianteVioladaException.class, () -> useCases.descartar(idEvidencia, usuario));
        verify(evidenciaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("descartar deve falhar quando a evidência não existe")
    void descartarInexistente() {
        when(evidenciaRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> useCases.descartar(idEvidencia, usuario));
    }
}
