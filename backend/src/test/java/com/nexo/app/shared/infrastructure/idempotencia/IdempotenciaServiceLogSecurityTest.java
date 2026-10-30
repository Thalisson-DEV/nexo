package com.nexo.app.shared.infrastructure.idempotencia;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.nexo.app.shared.infrastructure.logging.LogFingerprint;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotenciaServiceLogSecurityTest {

    @Test
    void conflitoNaoRegistraChaveIntegral() {
        RegistroIdempotenciaRepository repository = mock(RegistroIdempotenciaRepository.class);
        RegistroIdempotencia registro = mock(RegistroIdempotencia.class);
        String chave = "idempotencia-secreta\r\nERROR linha-forjada";
        when(repository.reservar(anyString(), any(), anyString(), anyString(), any(OffsetDateTime.class)))
                .thenReturn(0);
        when(repository.findById(chave)).thenReturn(Optional.of(registro));
        when(registro.pertenceA(any(UUID.class))).thenReturn(false);
        IdempotenciaService service = new IdempotenciaService(repository);
        ReflectionTestUtils.setField(service, "janelaHoras", 24L);
        Logger logger = (Logger) LoggerFactory.getLogger(IdempotenciaService.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.reservar(chave, UUID.randomUUID(), "POST", "/v1/recurso", "digest");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage())
                        .contains(LogFingerprint.de(chave))
                        .doesNotContain("idempotencia-secreta", "linha-forjada", "\r", "\n"));
    }
}
