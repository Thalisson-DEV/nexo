package com.nexo.app.shared.infrastructure.web;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RequisicaoDuplicadaException;
import com.nexo.app.shared.infrastructure.idempotencia.IdempotenciaService;
import com.nexo.app.shared.infrastructure.idempotencia.RegistroIdempotencia;
import com.nexo.app.shared.infrastructure.idempotencia.ResultadoReserva;
import com.nexo.app.shared.infrastructure.logging.LogFingerprint;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Faz valer a promessa de execução única para requisições carimbadas com
 * {@code Idempotency-Key}.
 *
 * <p>O problema que este filtro resolve não é técnico, é humano: quando o usuário não
 * recebe resposta, ele aperta de novo. Em 06/08/2026 isso gravou o mesmo desconto três
 * vezes. O servidor não tinha — e não tem, sem esta chave — como distinguir "outro
 * lançamento igual" de "o mesmo lançamento de novo", então obedecia. A chave é o cliente
 * dizendo qual das duas coisas ele quis.</p>
 *
 * <p><b>É opt-in.</b> Sem o header, tudo passa direto como sempre passou. Isso mantém
 * integrações e chamadas manuais funcionando, e deixa a adoção acontecer endpoint a
 * endpoint pelo lado do cliente, sem um big bang no servidor.</p>
 *
 * <p><b>Onde entra na cadeia:</b> depois da autenticação, porque a chave é escopada por
 * usuário e reservá-la antes de saber quem pede permitiria a um anônimo emperrar chaves
 * alheias. Por isso é montado dentro do {@code SecurityFilterChain}, e não como um
 * {@code @Component} — que o Boot registraria na cadeia do servlet, antes da segurança.</p>
 *
 * @author <b>Thalisson</b>
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotenciaFilter extends OncePerRequestFilter {

    public static final String HEADER_CHAVE = "Idempotency-Key";

    /** Marca a resposta que não executou nada: veio do registro. Útil no debug de campo. */
    public static final String HEADER_REPETIDA = "Idempotency-Replayed";

    private static final Set<String> METODOS_PROTEGIDOS = Set.of("POST", "PUT", "PATCH");
    private static final int TAMANHO_MAXIMO_CHAVE = 128;

    /**
     * Teto para bufferizar o corpo e calcular o digest. Payload de escrita em JSON não
     * chega perto disso; acima do teto o corpo passa direto, sem digest, e a conferência
     * de reuso de chave recai sobre método e caminho.
     */
    private static final int TAMANHO_MAXIMO_DIGEST = 2 * 1024 * 1024;

    private final IdempotenciaService idempotenciaService;
    private final HandlerExceptionResolver exceptionResolver;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !METODOS_PROTEGIDOS.contains(request.getMethod())
                || vazio(request.getHeader(HEADER_CHAVE));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String chave = request.getHeader(HEADER_CHAVE).trim();
        UUID idUsuario = SecurityUtils.getUsuarioLogadoId();

        // Requisição sem dono identificado não tem escopo para a chave. Passa direto:
        // recusar aqui transformaria o filtro em uma segunda camada de autenticação,
        // com regra própria, divergindo da que o SecurityConfig já declara.
        if (idUsuario == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (chave.length() > TAMANHO_MAXIMO_CHAVE) {
            exceptionResolver.resolveException(request, response, null, new InvarianteVioladaException(
                    "A chave de idempotência excede " + TAMANHO_MAXIMO_CHAVE + " caracteres."));
            return;
        }

        // Multipart fica de fora do buffer de propósito: são os 15 MB de anexo, e
        // duplicá-los em memória só para digerir o corpo trocaria uma duplicata de
        // registro por uma pressão de heap. Para eles, método e caminho já isolam o
        // recurso alvo o suficiente.
        HttpServletRequest requisicao = request;
        byte[] corpo = null;
        if (!ehMultipart(request) && cabeNoBuffer(request)) {
            corpo = StreamUtils.copyToByteArray(request.getInputStream());
            requisicao = new CorpoBufferizadoRequest(request, corpo);
        }

        String metodo = request.getMethod();
        String caminho = request.getRequestURI();
        String digest = corpo == null ? null : IdempotenciaService.digest(metodo, caminho, corpo);

        ResultadoReserva resultado = idempotenciaService.reservar(chave, idUsuario, metodo, caminho, digest);

        switch (resultado) {
            case ResultadoReserva.Repetir(RegistroIdempotencia registro) -> {
                log.info("Requisição repetida sem reexecutar: {} {} (chave {}).",
                        metodo, caminho, LogFingerprint.de(chave));
                repetir(response, registro);
            }
            case ResultadoReserva.EmVoo() -> exceptionResolver.resolveException(
                    request, response, null, new RequisicaoDuplicadaException(
                            "Esta solicitação já está sendo processada. Aguarde o resultado antes de "
                                    + "tentar de novo — reenviar agora criaria um registro duplicado."));
            case ResultadoReserva.Conflitante() -> exceptionResolver.resolveException(
                    request, response, null, new InvarianteVioladaException(
                            "Esta chave de idempotência já foi usada para outra operação."));
            case ResultadoReserva.Prosseguir() -> executar(requisicao, response, filterChain, chave, digest);
        }
    }

    /**
     * Executa de verdade, guardando a resposta para as retentativas futuras.
     *
     * <p>O corpo é capturado com {@link ContentCachingResponseWrapper} porque a retentativa
     * precisa do mesmo payload — o POST de desconto devolve o id que os passos seguintes
     * (itens, anexos) consomem. Uma retentativa que recebesse só "200, sem corpo" ficaria
     * sem o id e travaria no mesmo ponto onde o usuário já travou uma vez.</p>
     */
    private void executar(HttpServletRequest request, HttpServletResponse response,
                          FilterChain filterChain, String chave, String digest)
            throws ServletException, IOException {

        var respostaCapturada = new ContentCachingResponseWrapper(response);
        boolean sucesso = false;
        try {
            filterChain.doFilter(request, respostaCapturada);
            int status = respostaCapturada.getStatus();
            sucesso = status >= 200 && status < 300;

            if (sucesso) {
                idempotenciaService.concluir(chave, status, respostaCapturada.getContentType(),
                        respostaCapturada.getContentAsByteArray(), digest);
            }
        } finally {
            respostaCapturada.copyBodyToResponse();
            if (!sucesso) {
                // Erro (e exceção que sobe daqui) devolve a chave ao pool: o desfecho foi
                // sobre a tentativa, não sobre a intenção. Quem corrige e reenvia com a
                // mesma chave — que é o que a fila offline faz — precisa ser processado.
                liberarSilenciosamente(chave);
            }
        }
    }

    /** Repete a resposta gravada, byte a byte, para que a retentativa pareça um sucesso comum. */
    private void repetir(HttpServletResponse response, RegistroIdempotencia registro) throws IOException {
        response.setStatus(registro.getStatusHttp() == null ? 200 : registro.getStatusHttp());
        response.setHeader(HEADER_REPETIDA, "true");
        if (registro.getContentType() != null) {
            response.setHeader(HttpHeaders.CONTENT_TYPE, registro.getContentType());
        }
        if (registro.getCorpoResposta() != null) {
            byte[] bytes = registro.getCorpoResposta().getBytes(StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        }
        response.flushBuffer();
    }

    /**
     * A liberação nunca pode piorar o desfecho: se ela falhar, a requisição original já
     * tem sua resposta e a reserva órfã expira sozinha na janela configurada.
     */
    private void liberarSilenciosamente(String chave) {
        try {
            idempotenciaService.liberar(chave);
        } catch (RuntimeException e) {
            log.warn("Falha ao liberar chave de idempotência: chave={}, tipo={}",
                    LogFingerprint.de(chave), e.getClass().getSimpleName());
        }
    }

    private static boolean ehMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private static boolean cabeNoBuffer(HttpServletRequest request) {
        return request.getContentLengthLong() <= TAMANHO_MAXIMO_DIGEST;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
