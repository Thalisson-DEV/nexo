package com.nexo.app.shared.infrastructure.web;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.nexo.app.shared.domain.exception.SessaoInvalidaException;
import com.nexo.app.shared.domain.exception.BaseBusinessException;
import com.nexo.app.shared.domain.exception.ConflitoVersaoException;
import com.nexo.app.shared.domain.exception.EncriptografiaException;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.lookup.LookupRateLimitExceededException;
import jakarta.persistence.RollbackException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handler global de exceções utilizando o padrão RFC 7807 (Problem Details).
 * 
 * @author <b>Thalisson</b>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(LookupRateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleLookupRateLimitExceeded(
            LookupRateLimitExceededException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problemDetail.setTitle("Too Many Requests");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/rate-limit-exceeded"));
        problemDetail.setProperty("retryAfter", ex.getRetryAfter());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfter()))
                .body(problemDetail);
    }

    @ExceptionHandler(ConflitoVersaoException.class)
    public ProblemDetail handleConflitoVersaoException(ConflitoVersaoException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setType(ex.getType());
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("code", "VERSAO_DESATUALIZADA");
        problemDetail.setProperty("recurso", ex.getRecurso().name());
        problemDetail.setProperty("versaoEsperada", ex.getVersaoEsperada());
        problemDetail.setProperty("versaoAtual", ex.getVersaoAtual());
        return problemDetail;
    }

    /**
     * Erros de preenchimento de formulário dinâmico expõem o detalhamento campo a
     * campo na propriedade {@code erros}, para o frontend destacar tudo de uma vez.
     */
    @ExceptionHandler(PreenchimentoInvalidoException.class)
    public ProblemDetail handlePreenchimentoInvalidoException(PreenchimentoInvalidoException ex) {
        boolean possuiVersaoDesatualizada = ex.getErros() != null && ex.getErros().entrySet().stream()
                .anyMatch(erro -> "VERSAO_DESATUALIZADA".equals(
                        codigoTecnico(erro.getKey() + " " + erro.getValue())));
        HttpStatus status = possuiVersaoDesatualizada ? HttpStatus.CONFLICT : ex.getStatus();
        String codigo = possuiVersaoDesatualizada
                ? "VERSAO_DESATUALIZADA"
                : codigoTecnico(ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setType(possuiVersaoDesatualizada
                ? URI.create("https://app.exemplo.com/errors/versao_desatualizada")
                : ex.getType());
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("erros", ex.getErros());
        problemDetail.setProperty("fieldErrors", errosPorCampo(ex.getErros()));
        problemDetail.setProperty("code", codigo);
        return problemDetail;
    }

    @ExceptionHandler(InvarianteVioladaException.class)
    public ProblemDetail handleInvarianteVioladaException(InvarianteVioladaException ex) {
        String codigo = codigoTecnico(ex.getMessage());
        HttpStatus status = switch (codigo) {
            case "VERSAO_DESATUALIZADA", "CHAVE_IDEMPOTENCIA_REUTILIZADA" -> HttpStatus.CONFLICT;
            default -> ex.getStatus();
        };
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/" + codigo.toLowerCase(Locale.ROOT)));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("code", codigo);
        return problemDetail;
    }

    /**
     * Único 401 que encerra a sessão no aparelho. O {@code code} é o contrato com o frontend:
     * token de acesso vencido ou inválido se resolve renovando, isto aqui não.
     */
    @ExceptionHandler(SessaoInvalidaException.class)
    public ProblemDetail handleSessaoInvalida(SessaoInvalidaException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setType(ex.getType());
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("code", SessaoInvalidaException.CODIGO);
        return problemDetail;
    }

    @ExceptionHandler(BaseBusinessException.class)
    public ProblemDetail handleBusinessException(BaseBusinessException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        ex.getStatus(),
                        ex.getMessage()
                );
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setType(ex.getType());
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.FORBIDDEN,
                        "Permissões de acesso Negadas. Verifique suas credenciais e tente novamente."
                );
        problemDetail.setTitle("Acesso Negado");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/access-denied"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(InvalidDataAccessResourceUsageException.class)
    public ProblemDetail handleInvalidDataAccessResourceUsageException(InvalidDataAccessResourceUsageException ex) {
        log.error("Erro de uso de recurso de dados: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ocorreu um erro de comunicação ou sintaxe com o banco de dados. " +
                        "Isso pode ser causado por um filtro inválido ou inconsistência de tipos."
                );
        problemDetail.setTitle("Erro de Acesso a Dados");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/data-access-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler({
            EncriptografiaException.class,
            GeneralSecurityException.class,
    })
    public ProblemDetail handleEncryptionError(Exception ex) {

        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Falha na proteção de dados sensíveis. Verifique a integridade das chaves."
                );
        problemDetail.setTitle("Erro de Criptografia");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/encryption-failure"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ProblemDetail handleTokenExpiredException(TokenExpiredException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED,
                        "O token de acesso expirou. Por favor, realize um novo login."
                );
        problemDetail.setTitle("Token Expirado");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/token-expired"));
        problemDetail.setProperty("timestamp", Instant.now());
        // O frontend renova a sessão e repete a requisição; não é motivo para novo login.
        problemDetail.setProperty("code", "TOKEN_EXPIRADO");
        return problemDetail;
    }

    @ExceptionHandler(JWTVerificationException.class)
    public ProblemDetail handleJWTVerificationException(JWTVerificationException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.UNAUTHORIZED,
                        "Token de acesso inválido ou malformado."
                );
        problemDetail.setTitle("Token Inválido");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/invalid-token"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("code", "TOKEN_INVALIDO");
        return problemDetail;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {

        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.FORBIDDEN,
                        "Erro de autenticação, verifique suas credenciais."
                );
        problemDetail.setTitle("Erro de autenticação");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/bad-credentials"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage()
                );
        problemDetail.setTitle("Parâmetro Inválido");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/invalid-argument"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage()
                );
        problemDetail.setTitle("Estado Inválido");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/invalid-state"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage()
                );
        problemDetail.setTitle("Recurso não encontrado");
        problemDetail.setType(URI.create("https://app.exemplo.com/erros/recurso-nao-encontrado"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflito de concorrência detectado: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "O registro foi alterado por outro usuário ou processo. Por favor, recarregue os dados e tente novamente."
                );
        problemDetail.setTitle("Conflito de Concorrência");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/concurrency-conflict"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    /**
     * Arquivo maior que o teto de upload.
     *
     * <p>É um <b>override</b> do hook da superclasse, e não um {@code @ExceptionHandler}
     * novo: a partir do Spring 7 o {@code ResponseEntityExceptionHandler} já mapeia esta
     * exceção, e declarar um segundo handler para ela derruba o contexto por ambiguidade.</p>
     *
     * <p>Sem tratamento algum, o caso cairia no genérico e viraria 500 — que o cliente lê
     * como falha do servidor e portanto retenta. Era assim que um anexo grande demais virava
     * três tentativas de envio (e, antes da idempotência, três registros). 413 diz a verdade:
     * o pedido é que está errado, e retentar não muda isso.</p>
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        log.warn("Upload recusado por exceder o tamanho máximo: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.CONTENT_TOO_LARGE,
                        "O arquivo enviado excede o tamanho máximo permitido. Reduza o arquivo e envie novamente."
                );
        problemDetail.setTitle("Arquivo Muito Grande");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/upload-too-large"));
        problemDetail.setProperty("timestamp", Instant.now());
        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.CONTENT_TOO_LARGE, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Erro de integrity violation: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Violação de integridade dos dados, verifique seu input e tente novamente."
                );
        problemDetail.setTitle("Erro de integridade.");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/invalid-data"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            TransactionSystemException.class,
            RollbackException.class
    })
    public ProblemDetail handleConstraintViolation(Exception ex) {
        log.warn("Violação de restrição capturada: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Um ou mais campos estão inválidos. Faça o preenchimento correto e tente novamente."
                );
        problemDetail.setTitle("Campos Inválidos");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/invalid-fields"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;

    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Um ou mais campos estão inválidos. Faça o preenchimento correto e tente novamente."
                );
        problemDetail.setTitle("Campos Inválidos");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/invalid-fields"));
        problemDetail.setProperty("timestamp", Instant.now());
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(erro ->
                erros.putIfAbsent(erro.getField(), erro.getDefaultMessage()));
        problemDetail.setProperty("fieldErrors", errosPorCampo(erros));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    private Map<String, FieldError> errosPorCampo(Map<String, String> erros) {
        Map<String, FieldError> resultado = new LinkedHashMap<>();
        if (erros != null) {
            erros.forEach((campo, mensagem) -> resultado.put(campo,
                    new FieldError(codigoTecnico(campo + " " + mensagem), mensagem)));
        }
        return resultado;
    }

    private String codigoTecnico(String descricao) {
        String texto = descricao == null ? "" : descricao.toLowerCase(Locale.ROOT);
        if (texto.contains("ocorrencia") && texto.contains("obrig")) return "OCORRENCIA_OBRIGATORIA";
        if (texto.contains("ocorrencia") && (texto.contains("nao aplic") || texto.contains("não aplic"))) {
            return "OCORRENCIA_NAO_APLICAVEL";
        }
        if (texto.contains("classificacao") && texto.contains("incompat")) {
            return "CLASSIFICACAO_MATERIAL_INCOMPATIVEL";
        }
        if (texto.contains("reserva") && (texto.contains("registre") || texto.contains("obrigat"))) {
            return "RESERVA_OBRIGATORIA";
        }
        if (texto.contains("numero") && texto.contains("reserva") && texto.contains("digit")) {
            return "NUMERO_RESERVA_INVALIDO";
        }
        if (texto.contains("tipo") && (texto.contains("somente") || texto.contains("incompat"))) {
            return "TIPO_TECNICO_INCOMPATIVEL";
        }
        if (texto.contains("separacao") && (texto.contains("somente") || texto.contains("aplica"))) {
            return "SEPARACAO_NAO_APLICAVEL";
        }
        if (texto.contains("saldo insuficiente na obra")) return "SALDO_OBRA_INSUFICIENTE";
        if (texto.contains("serie") && texto.contains("obra")) return "SERIE_OBRA_INVALIDA";
        if (texto.contains("versao") && texto.contains("desatual")) return "VERSAO_DESATUALIZADA";
        if (texto.contains("chave de idempotencia") && texto.contains("outro payload")) {
            return "CHAVE_IDEMPOTENCIA_REUTILIZADA";
        }
        return "REGRA_NEGOCIO_VIOLADA";
    }

    private record FieldError(String code, String message) {
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidationException(ValidationException ex) {
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage()
                );
        problemDetail.setTitle("Violação de regra de negocio");
        problemDetail.setType(URI.create("https://app.exemplo.com/erros/violacao-de-regra-negocio"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        log.error("Erro inesperado no sistema: {}", ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail
                .forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ocorreu um erro interno no servidor. Por favor, contate o suporte técnico."
                );
        problemDetail.setTitle("Erro Interno");
        problemDetail.setType(URI.create("https://app.exemplo.com/errors/internal-server-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
