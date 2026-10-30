package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.AnexoDesvioResponse;
import com.nexo.app.modules.sst.application.dto.EvidenciaDownload;
import com.nexo.app.modules.sst.domain.AnexoDesvio;
import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.modules.sst.domain.DesvioSeguranca;
import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.InspecaoSeguranca;
import com.nexo.app.modules.sst.domain.TipoAnexo;
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
import java.util.List;
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
class AnexoDesvioUseCasesTest {

    @Mock private InspecaoSegurancaRepository repository;
    @Mock private ObjectStorageService objectStorage;

    @InjectMocks
    private AnexoDesvioUseCases useCases;

    private final UUID idInspecao = UUID.randomUUID();
    private final UUID idDesvio = UUID.randomUUID();
    private final UUID idAnexo = UUID.randomUUID();
    private final UUID colab = UUID.randomUUID();

    private InspecaoSeguranca inspecaoComDesvio() {
        InspecaoSeguranca insp = new InspecaoSeguranca(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                UUID.randomUUID(), OffsetDateTime.now(), "obs");
        insp.adicionarColaboradorInspecionado(colab);
        DesvioSeguranca desvio = insp.registrarDesvio(
                UUID.randomUUID(), UUID.randomUUID(), GravidadeDesvio.ALTA, "Câmera danificada", "Substituir", null, List.of(colab));
        ReflectionTestUtils.setField(insp, "id", idInspecao);
        ReflectionTestUtils.setField(desvio, "id", idDesvio);
        return insp;
    }

    private InspecaoSeguranca inspecaoComAnexo(String objectKey) {
        InspecaoSeguranca insp = inspecaoComDesvio();
        AnexoDesvio anexo = insp.getDesvios().get(0)
                .adicionarAnexo(ContextoAnexo.DESVIO, "foto.jpg", "image/jpeg", 3, objectKey);
        ReflectionTestUtils.setField(anexo, "id", idAnexo);
        return insp;
    }

    @Test
    @DisplayName("anexar deve enviar o arquivo ao R2 e registrar o anexo no desvio")
    void anexarComSucesso() {
        InspecaoSeguranca insp = inspecaoComDesvio();
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});

        AnexoDesvioResponse resp = useCases.anexar(idInspecao, idDesvio, ContextoAnexo.ACAO_CORRETIVA, arquivo);

        verify(objectStorage).enviar(anyString(), any(InputStream.class), eq(3L), eq("image/jpeg"));
        assertEquals(1, insp.getDesvios().get(0).getAnexos().size());
        assertEquals(ContextoAnexo.ACAO_CORRETIVA, resp.contexto());
        assertEquals(TipoAnexo.FOTO, resp.tipo());
        assertEquals("foto.jpg", resp.nomeArquivo());
        assertEquals("image/jpeg", resp.contentType());
        assertEquals(3L, resp.tamanhoBytes());
    }

    @Test
    @DisplayName("anexar deve aceitar PDF como DOCUMENTO")
    void anexarPdf() {
        InspecaoSeguranca insp = inspecaoComDesvio();
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "laudo.pdf", "application/pdf", new byte[]{9});

        AnexoDesvioResponse resp = useCases.anexar(idInspecao, idDesvio, ContextoAnexo.DESVIO, arquivo);

        assertEquals(TipoAnexo.DOCUMENTO, resp.tipo());
        verify(objectStorage).enviar(anyString(), any(InputStream.class), eq(1L), eq("application/pdf"));
    }

    @Test
    @DisplayName("anexar deve rejeitar tipo de arquivo não suportado e não tocar no R2")
    void anexarTipoInvalido() {
        InspecaoSeguranca insp = inspecaoComDesvio();
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));

        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "virus.exe", "application/x-msdownload", new byte[]{1});

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.anexar(idInspecao, idDesvio, ContextoAnexo.DESVIO, arquivo));
        verify(objectStorage, never()).enviar(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("anexar deve rejeitar arquivo acima de 15 MB")
    void anexarArquivoGrande() {
        InspecaoSeguranca insp = inspecaoComDesvio();
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));

        MultipartFile grande = mock(MultipartFile.class);
        when(grande.isEmpty()).thenReturn(false);
        when(grande.getContentType()).thenReturn("image/jpeg");
        when(grande.getSize()).thenReturn(15L * 1024 * 1024 + 1);

        assertThrows(InvarianteVioladaException.class,
                () -> useCases.anexar(idInspecao, idDesvio, ContextoAnexo.DESVIO, grande));
        verify(objectStorage, never()).enviar(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("anexar deve falhar quando a inspeção não existe")
    void anexarInspecaoInexistente() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[]{1});

        assertThrows(RecursoNaoEncontradoException.class,
                () -> useCases.anexar(idInspecao, idDesvio, ContextoAnexo.DESVIO, arquivo));
    }

    @Test
    @DisplayName("anexar deve falhar quando o desvio não pertence à inspeção")
    void anexarDesvioInexistente() {
        InspecaoSeguranca insp = inspecaoComDesvio();
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[]{1});

        assertThrows(RecursoNaoEncontradoException.class,
                () -> useCases.anexar(idInspecao, UUID.randomUUID(), ContextoAnexo.DESVIO, arquivo));
    }

    @Test
    @DisplayName("baixar deve recuperar o conteúdo do R2 com os metadados do anexo")
    void baixarComSucesso() {
        String objectKey = "sst/desvios/" + idDesvio + "/desvio/abc.jpg";
        InspecaoSeguranca insp = inspecaoComAnexo(objectKey);
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));
        InputStream conteudo = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(objectStorage.baixar(objectKey)).thenReturn(new StorageObject(conteudo, "image/jpeg", 3));

        EvidenciaDownload download = useCases.baixar(idInspecao, idDesvio, idAnexo);

        assertEquals("image/jpeg", download.contentType());
        assertEquals(3L, download.tamanhoBytes());
        assertEquals("foto.jpg", download.nomeArquivo());
        assertEquals(conteudo, download.conteudo());
    }

    @Test
    void baixarNaoAcessaAnexoDeInspecaoDesativada() {
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> useCases.baixar(idInspecao, idDesvio, idAnexo));
        verify(objectStorage, never()).baixar(anyString());
    }

    @Test
    @DisplayName("baixar deve falhar quando o anexo não existe")
    void baixarAnexoInexistente() {
        InspecaoSeguranca insp = inspecaoComAnexo("sst/desvios/" + idDesvio + "/desvio/abc.jpg");
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));

        assertThrows(RecursoNaoEncontradoException.class,
                () -> useCases.baixar(idInspecao, idDesvio, UUID.randomUUID()));
    }

    @Test
    @DisplayName("remover deve excluir o anexo do desvio e o objeto no R2")
    void removerComSucesso() {
        String objectKey = "sst/desvios/" + idDesvio + "/desvio/abc.jpg";
        InspecaoSeguranca insp = inspecaoComAnexo(objectKey);
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        useCases.remover(idInspecao, idDesvio, idAnexo);

        verify(objectStorage).remover(objectKey);
        assertEquals(0, insp.getDesvios().get(0).getAnexos().size());
    }

    @Test
    @DisplayName("remover deve concluir mesmo se a exclusão no R2 falhar (best-effort)")
    void removerToleranteAFalhaNoR2() {
        String objectKey = "sst/desvios/" + idDesvio + "/desvio/abc.jpg";
        InspecaoSeguranca insp = inspecaoComAnexo(objectKey);
        when(repository.findByIdAndAtivoTrue(idInspecao)).thenReturn(Optional.of(insp));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ArmazenamentoException("indisponível")).when(objectStorage).remover(objectKey);

        useCases.remover(idInspecao, idDesvio, idAnexo); // não deve lançar

        assertEquals(0, insp.getDesvios().get(0).getAnexos().size());
    }
}
