package com.nexo.app.modules.estoque.application.services;

import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.infrastructure.persistence.OperacaoSolicitacaoMaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AssinaturaSolicitacaoMaterialServiceTest {

    private static final byte[] PNG_MINIMO = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    @Mock private ObjectStorageService armazenamento;
    @Mock private SolicitacaoMaterialRepository solicitacoes;
    @Mock private OperacaoSolicitacaoMaterialRepository operacoes;
    @InjectMocks private AssinaturaSolicitacaoMaterialService service;

    @Test
    void converteDataUrlEmObjetosPngEDevolveSomenteAsReferencias() {
        UUID idSolicitacao = UUID.randomUUID();
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_MINIMO);

        var resultado = service.armazenar(idSolicitacao, dataUrl, dataUrl);

        assertThat(resultado.almox()).startsWith("objeto:estoque/solicitacoes/" + idSolicitacao);
        assertThat(resultado.equipe()).startsWith("objeto:estoque/solicitacoes/" + idSolicitacao);
        ArgumentCaptor<InputStream> conteudo = ArgumentCaptor.forClass(InputStream.class);
        verify(armazenamento).enviar(
                eq(resultado.almox().substring("objeto:".length())), conteudo.capture(),
                eq((long) PNG_MINIMO.length), eq("image/png"));
    }

    @Test
    void preservaMarcadorDeAssinaturaFisicaSemEnviarArquivo() {
        var resultado = service.armazenar(UUID.randomUUID(), "FISICA", "FISICA");

        assertThat(resultado.almox()).isEqualTo("FISICA");
        assertThat(resultado.equipe()).isEqualTo("FISICA");
    }
}
