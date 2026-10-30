package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.application.dto.DescontoBaixaRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoItemRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoValidacaoRequest;
import com.nexo.app.modules.desconto.application.mappers.DescontoMapper;
import com.nexo.app.modules.desconto.domain.Desconto;
import com.nexo.app.modules.desconto.domain.MotivoDesconto;
import com.nexo.app.modules.desconto.domain.StatusDesconto;
import com.nexo.app.modules.desconto.domain.TipoEquipamento;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DescontoUseCasesTest {

    @Mock private DescontoRepository repository;
    @Mock private DescontoMapper mapper;
    @Mock private MotivoDescontoUseCases motivoUseCases;
    @Mock private TipoEquipamentoUseCases equipamentoUseCases;
    @Mock private ObjectStorageService storageService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DescontoUseCases useCases;

    private final UUID colaboradorId = UUID.randomUUID();
    private final UUID solicitanteId = UUID.randomUUID();
    private final UUID validadorId = UUID.randomUUID();
    private final UUID baixaId = UUID.randomUUID();
    private final UUID motivoId = UUID.randomUUID();
    private final UUID equipamentoId = UUID.randomUUID();
    private final UUID descontoId = UUID.randomUUID();

    private MotivoDesconto buildMotivo() {
        var m = new MotivoDesconto("Mau Uso", 5);
        ReflectionTestUtils.setField(m, "id", motivoId);
        return m;
    }

    private TipoEquipamento buildEquipamento() {
        var eq = new TipoEquipamento("Ferramental");
        ReflectionTestUtils.setField(eq, "id", equipamentoId);
        return eq;
    }

    private Desconto buildDesconto(MotivoDesconto motivo) {
        var d = new Desconto(colaboradorId, motivo, "Obs", 3, OffsetDateTime.now(), solicitanteId);
        ReflectionTestUtils.setField(d, "id", descontoId);
        return d;
    }

    @Test
    @DisplayName("Deve solicitar desconto com sucesso")
    void deveSolicitarDesconto() {
        var motivo = buildMotivo();
        when(motivoUseCases.buscarEntity(motivoId)).thenReturn(motivo);

        var request = new DescontoRequest(colaboradorId, motivoId, "Teste", 3, OffsetDateTime.now());
        useCases.solicitar(request, solicitanteId);

        verify(repository).save(any(Desconto.class));
    }

    @Test
    @DisplayName("Não deve permitir solicitar desconto com parcelas superiores ao teto do motivo")
    void naoDeveSolicitarAcimaTeto() {
        var motivo = buildMotivo(); // teto = 5
        when(motivoUseCases.buscarEntity(motivoId)).thenReturn(motivo);

        var request = new DescontoRequest(colaboradorId, motivoId, "Teste", 6, OffsetDateTime.now()); // Acima de 5
        
        assertThrows(InvarianteVioladaException.class, () -> useCases.solicitar(request, solicitanteId));
    }

    @Test
    @DisplayName("Deve validar o desconto apenas se tiver itens")
    void deveValidarApenasComItens() {
        var motivo = buildMotivo();
        var desconto = buildDesconto(motivo);
        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        var validacao = new DescontoValidacaoRequest(new BigDecimal("150.00"));

        // Sem itens ainda
        assertThrows(InvarianteVioladaException.class, () -> useCases.validar(descontoId, validadorId, validacao));

        // Adicionando um item
        var eq = buildEquipamento();
        desconto.adicionarItem("Furadeira", eq, new BigDecimal("150.00"));

        assertDoesNotThrow(() -> useCases.validar(descontoId, validadorId, validacao));
        assertEquals(StatusDesconto.VALIDADO, desconto.getStatus());
        assertNotNull(desconto.getDataValidacao());
        assertEquals(validadorId, desconto.getUsuarioValidadorId());
    }

    @Test
    @DisplayName("Valor sugerido deve refletir o somatório dos itens lançados")
    void valorSugeridoDeveSomarItens() {
        var motivo = buildMotivo();
        var eq = buildEquipamento();
        var desconto = buildDesconto(motivo);

        assertEquals(0, BigDecimal.ZERO.compareTo(desconto.getValorSugerido()));

        desconto.adicionarItem("Furadeira", eq, new BigDecimal("150.00"));
        desconto.adicionarItem("Lixadeira", eq, new BigDecimal("200.50"));

        assertEquals(0, new BigDecimal("350.50").compareTo(desconto.getValorSugerido()));
    }

    @Test
    @DisplayName("Deve registrar valor, usuário e data em cada etapa do desconto")
    void deveRastrearValoresPorEtapa() {
        var motivo = buildMotivo();
        var eq = buildEquipamento();
        var desconto = buildDesconto(motivo);
        desconto.adicionarItem("Furadeira", eq, new BigDecimal("150.00"));

        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        useCases.validar(descontoId, validadorId, new DescontoValidacaoRequest(new BigDecimal("120.00")));
        useCases.baixar(descontoId, baixaId, new DescontoBaixaRequest(new BigDecimal("100.00")));

        // Etapa 1 - sugestão (somatório dos itens)
        assertEquals(0, new BigDecimal("150.00").compareTo(desconto.getValorSugerido()));
        assertEquals(solicitanteId, desconto.getUsuarioSolicitanteId());

        // Etapa 2 - validação
        assertEquals(0, new BigDecimal("120.00").compareTo(desconto.getValorValidado()));
        assertEquals(validadorId, desconto.getUsuarioValidadorId());
        assertNotNull(desconto.getDataValidacao());

        // Etapa 3 - baixa (valor que conta no fim)
        assertEquals(0, new BigDecimal("100.00").compareTo(desconto.getValorBaixado()));
        assertEquals(baixaId, desconto.getUsuarioBaixaId());
        assertNotNull(desconto.getDataBaixa());
    }

    @Test
    @DisplayName("Deve alterar o termo por completo, inclusive o colaborador, antes da validação")
    void deveAlterarTermoAntesDaValidacao() {
        var motivo = buildMotivo();
        var desconto = buildDesconto(motivo);
        var outroColaborador = UUID.randomUUID();

        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));
        when(motivoUseCases.buscarEntity(motivoId)).thenReturn(motivo);

        var request = new DescontoRequest(outroColaborador, motivoId, "Corrigido", 2, OffsetDateTime.now());
        useCases.atualizar(descontoId, request);

        assertEquals(outroColaborador, desconto.getColaboradorId());
        assertEquals("Corrigido", desconto.getObservacoes());
        assertEquals(2, desconto.getQuantidadeParcelas());
    }

    @Test
    @DisplayName("Não deve alterar o termo após a validação")
    void naoDeveAlterarTermoAposValidacao() {
        var motivo = buildMotivo();
        var eq = buildEquipamento();
        var desconto = buildDesconto(motivo);
        desconto.adicionarItem("Furadeira", eq, new BigDecimal("150.00"));
        desconto.validar(validadorId, new BigDecimal("150.00"));

        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));
        when(motivoUseCases.buscarEntity(motivoId)).thenReturn(motivo);

        var request = new DescontoRequest(colaboradorId, motivoId, "Tentativa", 2, OffsetDateTime.now());

        assertThrows(InvarianteVioladaException.class, () -> useCases.atualizar(descontoId, request));
    }

    @Test
    @DisplayName("Não deve permitir alterar itens após validação")
    void naoDeveAlterarItensPosValidacao() {
        var motivo = buildMotivo();
        var eq = buildEquipamento();
        var desconto = buildDesconto(motivo);
        desconto.adicionarItem("Furadeira", eq, new BigDecimal("150.00"));
        desconto.validar(validadorId, new BigDecimal("150.00")); // Status -> VALIDADO

        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));
        when(equipamentoUseCases.buscarEntity(equipamentoId)).thenReturn(eq);

        var request = new DescontoItemRequest("Lixadeira", equipamentoId, new BigDecimal("200.00"));
        
        var ex = assertThrows(InvarianteVioladaException.class, () -> useCases.adicionarItem(descontoId, request));
        assertEquals("Não é possível adicionar itens a um desconto que não está no status SOLICITADO.", ex.getMessage());
    }
    
    @Test
    @DisplayName("Deve baixar um desconto validado com sucesso")
    void deveBaixarDesconto() {
        var motivo = buildMotivo();
        var eq = buildEquipamento();
        var desconto = buildDesconto(motivo);
        desconto.adicionarItem("Furadeira", eq, new BigDecimal("150.00"));
        desconto.validar(validadorId, new BigDecimal("150.00")); // Status -> VALIDADO

        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        useCases.baixar(descontoId, baixaId, new DescontoBaixaRequest(new BigDecimal("150.00")));

        assertEquals(StatusDesconto.BAIXADO, desconto.getStatus());
        assertNotNull(desconto.getDataBaixa());
    }
    
    @Test
    @DisplayName("Não deve baixar um desconto não validado")
    void naoDeveBaixarNaoValidado() {
        var motivo = buildMotivo();
        var desconto = buildDesconto(motivo);
        
        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        // Status atual é SOLICITADO
        var baixa = new DescontoBaixaRequest(new BigDecimal("150.00"));
        assertThrows(InvarianteVioladaException.class, () -> useCases.baixar(descontoId, baixaId, baixa));
    }

    @Test
    @DisplayName("Deve permitir adicionar anexo de áudio OGG com sucesso")
    void devePermitirAnexoAudioOgg() {
        var motivo = buildMotivo();
        var desconto = buildDesconto(motivo);
        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        var arquivo = new MockMultipartFile(
                "arquivo", "gravacao.ogg", "audio/ogg", "audio-content".getBytes());

        assertDoesNotThrow(() -> useCases.adicionarAnexo(descontoId, arquivo));
        assertEquals(1, desconto.getAnexos().size());
        assertEquals("audio/ogg", desconto.getAnexos().get(0).getContentType());
        verify(repository).save(desconto);
    }

    @Test
    @DisplayName("Deve permitir adicionar anexo de vídeo MP4 com sucesso")
    void devePermitirAnexoVideoMp4() {
        var motivo = buildMotivo();
        var desconto = buildDesconto(motivo);
        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        var arquivo = new MockMultipartFile(
                "arquivo", "evidencia.mp4", "video/mp4", "video-content".getBytes());

        assertDoesNotThrow(() -> useCases.adicionarAnexo(descontoId, arquivo));
        assertEquals(1, desconto.getAnexos().size());
        assertEquals("video/mp4", desconto.getAnexos().get(0).getContentType());
        verify(repository).save(desconto);
    }

    @Test
    @DisplayName("Deve inferir tipo de áudio OGG pela extensão quando contentType for genérico")
    void deveInferirAudioOggPorExtensao() {
        var motivo = buildMotivo();
        var desconto = buildDesconto(motivo);
        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        var arquivo = new MockMultipartFile(
                "arquivo", "depoimento.ogg", "application/octet-stream", "audio-content".getBytes());

        assertDoesNotThrow(() -> useCases.adicionarAnexo(descontoId, arquivo));
        assertEquals(1, desconto.getAnexos().size());
        assertEquals("audio/ogg", desconto.getAnexos().get(0).getContentType());
        verify(repository).save(desconto);
    }

    @Test
    @DisplayName("Deve rejeitar anexo com formato não suportado")
    void deveRejeitarAnexoNaoPermitido() {
        var motivo = buildMotivo();
        var desconto = buildDesconto(motivo);
        when(repository.findById(descontoId)).thenReturn(Optional.of(desconto));

        var arquivo = new MockMultipartFile(
                "arquivo", "virus.exe", "application/x-msdownload", "bin".getBytes());

        var ex = assertThrows(InvarianteVioladaException.class, () -> useCases.adicionarAnexo(descontoId, arquivo));
        assertTrue(ex.getMessage().contains("Tipo de arquivo não suportado"));
    }
}
