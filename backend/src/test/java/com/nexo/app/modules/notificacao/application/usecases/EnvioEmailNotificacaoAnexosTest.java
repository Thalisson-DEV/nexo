package com.nexo.app.modules.notificacao.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.notificacao.application.dto.NotificacaoResponse;
import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;
import com.nexo.app.modules.notificacao.domain.LogEmail;
import com.nexo.app.modules.notificacao.domain.PrioridadeNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.LogEmailRepository;
import com.nexo.app.shared.application.notificacao.AnexoNotificacao;
import com.nexo.app.shared.application.notificacao.FornecedorAnexosNotificacao;
import com.nexo.app.shared.infrastructure.email.EmailProperties;
import com.nexo.app.shared.infrastructure.email.MensagemEmail;
import com.nexo.app.shared.infrastructure.email.MontadorEmailHtml;
import com.nexo.app.shared.infrastructure.email.ProvedorEmail;
import com.nexo.app.shared.infrastructure.email.ResultadoEnvioEmail;
import com.nexo.app.shared.infrastructure.email.ServicoEmail;
import com.nexo.app.shared.infrastructure.storage.ObjectStorageService;
import com.nexo.app.shared.infrastructure.storage.StorageObject;

class EnvioEmailNotificacaoAnexosTest {

    @Test
    void consultaEBaixaAnexosAntesDeChamarOProvedorDeEmail() {
        UUID usuarioId = UUID.randomUUID();
        UUID notificacaoId = UUID.randomUUID();
        UUID solicitacaoId = UUID.randomUUID();
        String gatilho = "financeiro.solicitacao_submetida";

        UsuarioApi usuarioApi = mock(UsuarioApi.class);
        LogEmailRepository logRepository = mock(LogEmailRepository.class);
        FornecedorAnexosNotificacao fornecedor = mock(FornecedorAnexosNotificacao.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        ServicoEmail servicoEmail = mock(ServicoEmail.class);

        when(usuarioApi.buscarEmailsPorIds(Set.of(usuarioId)))
                .thenReturn(Map.of(usuarioId, "diretor@exemplo.com.br"));
        when(logRepository.save(any(LogEmail.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(logRepository.saveAll(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        when(fornecedor.atende(gatilho)).thenReturn(true);
        when(fornecedor.anexosDe(solicitacaoId)).thenReturn(List.of(
                new AnexoNotificacao("pedido.pdf", "application/pdf", "financeiro/pedido.pdf", 3)));
        when(storage.baixar("financeiro/pedido.pdf")).thenReturn(new StorageObject(
                new ByteArrayInputStream(new byte[]{7, 8, 9}), "application/pdf", 3));
        when(servicoEmail.enviar(any(MensagemEmail.class)))
                .thenReturn(ResultadoEnvioEmail.entregue(ProvedorEmail.SMTP, null));

        EmailProperties propriedades = new EmailProperties();
        MontadorEmailNotificacao montador = new MontadorEmailNotificacao(
                propriedades, new MontadorEmailHtml(propriedades), storage, List.of(fornecedor), List.of());
        EnvioEmailNotificacao envio = new EnvioEmailNotificacao(
                usuarioApi, logRepository, montador, servicoEmail);
        NotificacaoResponse notificacao = new NotificacaoResponse(
                notificacaoId, null, solicitacaoId,
                CategoriaNotificacao.APROVACAO, "Aprovações",
                PrioridadeNotificacao.NORMAL, "Normal",
                "Compra aguardando aprovação", "Material elétrico", null,
                Map.of(), false, null, null, null);

        envio.enviar(usuarioId, notificacao, gatilho);

        var ordem = inOrder(fornecedor, storage, servicoEmail);
        ordem.verify(fornecedor).anexosDe(solicitacaoId);
        ordem.verify(storage).baixar("financeiro/pedido.pdf");
        ArgumentCaptor<MensagemEmail> mensagem = ArgumentCaptor.forClass(MensagemEmail.class);
        ordem.verify(servicoEmail).enviar(mensagem.capture());
        assertThat(mensagem.getValue().anexos()).singleElement()
                .satisfies(anexo -> assertThat(anexo.nomeArquivo()).isEqualTo("pedido.pdf"));
    }
}
