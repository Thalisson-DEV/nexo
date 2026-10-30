package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.MovimentacaoEstoque;
import com.nexo.app.modules.estoque.infrastructure.persistence.MovimentacaoEstoqueRepository;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssinaturaTransferenciaServiceTest {

    private static final byte[] PNG_MINIMO = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    @Mock private ObjectStorageService armazenamento;
    @Mock private MovimentacaoEstoqueRepository movimentacoes;
    @InjectMocks private AssinaturaTransferenciaService service;

    @Test
    void converteDataUrlEmObjetosPngEDevolveSomenteAsReferencias() {
        UUID idReferencia = UUID.randomUUID();
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_MINIMO);

        var resultado = service.armazenar(idReferencia, dataUrl, dataUrl);

        assertThat(resultado.entregador()).startsWith("objeto:estoque/transferencias/" + idReferencia);
        assertThat(resultado.recebedor()).startsWith("objeto:estoque/transferencias/" + idReferencia);
        ArgumentCaptor<InputStream> conteudo = ArgumentCaptor.forClass(InputStream.class);
        verify(armazenamento).enviar(
                eq(resultado.entregador().substring("objeto:".length())), conteudo.capture(),
                eq((long) PNG_MINIMO.length), eq("image/png"));
    }

    @Test
    void preservaMarcadorDeAssinaturaFisicaSemEnviarArquivo() {
        var resultado = service.armazenar(UUID.randomUUID(), "FISICA", "FISICA");

        assertThat(resultado.entregador()).isEqualTo("FISICA");
        assertThat(resultado.recebedor()).isEqualTo("FISICA");
    }

    @Test
    void baixar_DeveRetornarVazioParaAssinaturaFisica() {
        UUID idReferencia = UUID.randomUUID();
        MovimentacaoEstoque base = mock(MovimentacaoEstoque.class);
        when(base.getAssinaturaEntregador()).thenReturn("FISICA");
        when(movimentacoes.findAllByIdReferenciaOperacaoInOrderByDataHoraAscIdAsc(List.of(idReferencia)))
                .thenReturn(List.of(base));

        var opt = service.baixar(idReferencia, AssinaturaTransferenciaService.ParteTransferencia.ENTREGADOR);

        assertThat(opt).isEmpty();
    }
}
