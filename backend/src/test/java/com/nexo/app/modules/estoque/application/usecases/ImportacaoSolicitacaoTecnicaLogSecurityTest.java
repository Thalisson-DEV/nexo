package com.nexo.app.modules.estoque.application.usecases;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ProcessamentoImportacao;
import com.nexo.app.modules.estoque.application.services.LeitorArquivoImportacaoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.ModeloImportacaoSolicitacaoTecnicaService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.ValidadorImportacaoSolicitacaoTecnicaService;
import com.nexo.app.modules.estoque.infrastructure.persistence.LoteImportacaoSolicitacaoRepository;
import com.nexo.app.shared.infrastructure.logging.LogFingerprint;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportacaoSolicitacaoTecnicaLogSecurityTest {

    @Test
    void falhaDePreenchimentoRetornaTipoECampoParaCorrecao() {
        ConfirmadorGrupoImportacaoSolicitacaoTecnica confirmador =
                mock(ConfirmadorGrupoImportacaoSolicitacaoTecnica.class);
        when(confirmador.confirmar(any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(new PreenchimentoInvalidoException(Map.of(
                        "numero", "O numero da reserva deve conter de 1 a 20 digitos.")));
        ImportacaoSolicitacaoTecnicaUseCases useCases = new ImportacaoSolicitacaoTecnicaUseCases(
                mock(LeitorArquivoImportacaoSolicitacaoTecnica.class),
                mock(ValidadorImportacaoSolicitacaoTecnicaService.class),
                mock(ModeloImportacaoSolicitacaoTecnicaService.class),
                mock(LoteImportacaoSolicitacaoRepository.class), confirmador,
                new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));

        var resposta = useCases.confirmar(
                new ConfirmarImportacaoSolicitacaoTecnicaRequest(
                        "token-da-previa",
                        List.of(new ConfirmarImportacaoSolicitacaoTecnicaRequest.ChaveTipo("SOL-001", "221")),
                        true),
                UUID.randomUUID(), "idempotency-key");

        assertThat(resposta.resultados()).singleElement().satisfies(resultado -> {
            assertThat(resultado.tipo()).isEqualTo("221");
            assertThat(resultado.erro()).contains("Número da reserva:", "1 a 20 digitos");
        });
        assertThat(resposta.processamento().etapa()).isEqualTo(ProcessamentoImportacao.Etapa.CONFIRMACAO);
        assertThat(resposta.processamento().estado()).isEqualTo(ProcessamentoImportacao.Estado.FALHOU);
    }

    @Test
    void falhaDeGrupoNaoRegistraChaveLivreIntegral() {
        String chaveGrupo = "grupo-secreto\r\nERROR linha-forjada";
        ConfirmadorGrupoImportacaoSolicitacaoTecnica confirmador =
                mock(ConfirmadorGrupoImportacaoSolicitacaoTecnica.class);
        when(confirmador.confirmar(any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(new IllegalStateException("falha controlada"));
        ImportacaoSolicitacaoTecnicaUseCases useCases = new ImportacaoSolicitacaoTecnicaUseCases(
                mock(LeitorArquivoImportacaoSolicitacaoTecnica.class),
                mock(ValidadorImportacaoSolicitacaoTecnicaService.class),
                mock(ModeloImportacaoSolicitacaoTecnicaService.class),
                mock(LoteImportacaoSolicitacaoRepository.class), confirmador,
                new ObservabilidadeSolicitacaoTecnica(new SimpleMeterRegistry()));
        Logger logger = (Logger) LoggerFactory.getLogger(ImportacaoSolicitacaoTecnicaUseCases.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            useCases.confirmar(
                    new ConfirmarImportacaoSolicitacaoTecnicaRequest(
                            "token-da-previa",
                            List.of(new ConfirmarImportacaoSolicitacaoTecnicaRequest.ChaveTipo(
                                    chaveGrupo, "221")),
                            true),
                    UUID.randomUUID(), "idempotency-key");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage())
                        .contains(LogFingerprint.de(chaveGrupo))
                        .doesNotContain("grupo-secreto", "linha-forjada", "\r", "\n"));
    }
}
