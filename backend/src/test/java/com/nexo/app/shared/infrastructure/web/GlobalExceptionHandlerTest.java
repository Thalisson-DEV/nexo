package com.nexo.app.shared.infrastructure.web;

import com.nexo.app.shared.infrastructure.lookup.LookupRateLimitExceededException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rateLimitRetornaRfc7807429ERetryAfter() {
        var response = handler.handleLookupRateLimitExceeded(new LookupRateLimitExceededException(3));

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(429);
        org.assertj.core.api.Assertions.assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("3");
        org.assertj.core.api.Assertions.assertThat(response.getBody().getProperties())
                .containsEntry("retryAfter", 3L);
    }

    @Test
    void expoeErrosTecnicosPorCampoComCodigoEstavel() {
        var problem = handler.handlePreenchimentoInvalidoException(
                new PreenchimentoInvalidoException(Map.of("ocorrencia", "A ocorrencia e obrigatoria.")));

        assertThat(problem.getProperties()).containsEntry("code", "OCORRENCIA_OBRIGATORIA");
        assertThat(problem.getProperties().get("fieldErrors").toString())
                .contains("OCORRENCIA_OBRIGATORIA");
    }

    @Test
    void retornaConflitoParaVersaoObsoleta() {
        var problem = handler.handleInvarianteVioladaException(
                new InvarianteVioladaException("A versao da solicitacao esta desatualizada."));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "VERSAO_DESATUALIZADA");
    }

    @Test
    void retornaVersoesEstruturadasParaRecarregarRecurso() {
        var problem = handler.handleConflitoVersaoException(new ConflitoVersaoException(
                ConflitoVersaoException.Recurso.SOLICITACAO, 3L, 4L));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties())
                .containsEntry("code", "VERSAO_DESATUALIZADA")
                .containsEntry("recurso", "SOLICITACAO")
                .containsEntry("versaoEsperada", 3L)
                .containsEntry("versaoAtual", 4L);
    }

    @Test
    void retornaConflitoPreservandoErrosPorFilhoQuandoVersaoEstaObsoleta() {
        var problem = handler.handlePreenchimentoInvalidoException(
                new PreenchimentoInvalidoException(Map.of(
                        "versoesFilhos[77777777-7777-7777-7777-777777777771]",
                        "A versao do filho esta desatualizada.")));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "VERSAO_DESATUALIZADA");
        assertThat(problem.getProperties().get("fieldErrors").toString())
                .contains("VERSAO_DESATUALIZADA")
                .contains("versoesFilhos");
    }

    @Test
    void retornaForbiddenParaAutoridadeAusente() {
        var problem = handler.handleAuthorizationDeniedException(new AuthorizationDeniedException("negado"));

        assertThat(problem.getStatus()).isEqualTo(403);
    }
}
