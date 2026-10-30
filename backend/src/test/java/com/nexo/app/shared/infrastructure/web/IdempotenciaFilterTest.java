package com.nexo.app.shared.infrastructure.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.core.read.ListAppender;
import com.nexo.app.shared.domain.exception.RequisicaoDuplicadaException;
import com.nexo.app.shared.infrastructure.idempotencia.IdempotenciaService;
import com.nexo.app.shared.infrastructure.idempotencia.RegistroIdempotencia;
import com.nexo.app.shared.infrastructure.idempotencia.ResultadoReserva;
import com.nexo.app.shared.infrastructure.logging.LogFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O contrato que impede a repetição do incidente de 06/08/2026 (mesmo desconto gravado
 * três vezes porque o usuário, sem resposta na tela, apertou salvar de novo).
 *
 * <p>Cada teste aqui corresponde a um degrau real daquele dia: o clique repetido enquanto
 * a primeira requisição ainda corria, o clique repetido depois que ela terminou, e o
 * reenvio legítimo depois de um erro de validação.</p>
 */
@ExtendWith(MockitoExtension.class)
class IdempotenciaFilterTest {

    private static final String CHAVE = "3f7c1e10-0000-4000-8000-000000000001";
    private static final UUID USUARIO = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private IdempotenciaService idempotenciaService;
    @Mock private HandlerExceptionResolver exceptionResolver;

    private IdempotenciaFilter filter;

    @BeforeEach
    void autenticar() {
        filter = new IdempotenciaFilter(idempotenciaService, exceptionResolver);
        // Principal como String é um dos dois formatos que o SecurityUtils reconhece — e o
        // único que não depende da visibilidade da classe do teste na hora da reflexão.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USUARIO.toString(), null, List.of()));
    }

    @Test
    @DisplayName("sem o header, nada muda: a requisição passa como sempre passou")
    void semHeaderPassaDireto() throws Exception {
        var request = requisicao("POST", "/v1/descontos", "{\"motivoId\":\"x\"}");
        request.removeHeader(IdempotenciaFilter.HEADER_CHAVE);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).as("handler executado").isNotNull();
        verify(idempotenciaService, never()).reservar(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("GET nunca é interceptado — a promessa é sobre escrita")
    void leituraNaoEhInterceptada() throws Exception {
        var request = requisicao("GET", "/v1/descontos", null);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        verify(idempotenciaService, never()).reservar(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("chave inédita executa de verdade e guarda a resposta para as retentativas")
    void chaveIneditaExecutaEGuarda() throws Exception {
        when(idempotenciaService.reservar(eq(CHAVE), eq(USUARIO), eq("POST"), eq("/v1/descontos"), anyString()))
                .thenReturn(new ResultadoReserva.Prosseguir());

        var request = requisicao("POST", "/v1/descontos", "{\"motivoId\":\"x\"}");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).setStatus(201);
            res.setContentType("application/json");
            res.getOutputStream().write("{\"id\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
        });

        var corpo = ArgumentCaptor.forClass(byte[].class);
        verify(idempotenciaService).concluir(eq(CHAVE), eq(201), eq("application/json"), corpo.capture(), anyString());
        assertThat(new String(corpo.getValue(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"abc\"}");
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"abc\"}");
        verify(idempotenciaService, never()).liberar(anyString());
    }

    @Test
    @DisplayName("o corpo continua legível pelo handler mesmo depois de digerido pelo filtro")
    void corpoBufferizadoChegaInteiroAoHandler() throws Exception {
        when(idempotenciaService.reservar(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoReserva.Prosseguir());

        String payload = "{\"motivoId\":\"x\",\"quantidadeParcelas\":3}";
        var request = requisicao("POST", "/v1/descontos", payload);
        var lido = new StringBuilder();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            lido.append(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            ((HttpServletResponse) res).setStatus(201);
        });

        assertThat(lido.toString()).isEqualTo(payload);
    }

    @Test
    @DisplayName("segundo clique com a primeira requisição ainda em voo é recusado, não executado")
    void cliqueDuranteRequisicaoEmVooNaoExecuta() throws Exception {
        when(idempotenciaService.reservar(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoReserva.EmVoo());

        var executou = new AtomicInteger();
        var request = requisicao("POST", "/v1/descontos", "{}");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> executou.incrementAndGet());

        assertThat(executou.get()).as("handler NÃO pode rodar").isZero();
        verify(exceptionResolver).resolveException(any(), any(), any(), any(RequisicaoDuplicadaException.class));
    }

    @org.junit.jupiter.api.AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("clique repetido depois do sucesso repete a resposta original, sem gravar de novo")
    void repeticaoDevolveRespostaGravadaSemExecutar() throws Exception {
        var registro = registroConcluido(201, "application/json", "{\"id\":\"abc\"}");
        when(idempotenciaService.reservar(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoReserva.Repetir(registro));

        var executou = new AtomicInteger();
        var request = requisicao("POST", "/v1/descontos", "{}");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> executou.incrementAndGet());

        assertThat(executou.get()).as("handler NÃO pode rodar de novo").isZero();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"abc\"}");
        assertThat(response.getHeader(IdempotenciaFilter.HEADER_REPETIDA))
                .as("a resposta se identifica como repetida, para o debug de campo").isEqualTo("true");
    }

    @Test
    void logDeRepeticaoUsaFingerprintSemValorIntegralOuQuebraDeLinha() throws Exception {
        String chaveControlada = "chave-secreta\r\nWARN linha-forjada";
        var registro = registroConcluido(200, "application/json", "{}");
        when(idempotenciaService.reservar(eq(chaveControlada), eq(USUARIO), eq("POST"),
                eq("/v1/descontos"), anyString())).thenReturn(new ResultadoReserva.Repetir(registro));
        var request = requisicao("POST", "/v1/descontos", "{}");
        request.removeHeader(IdempotenciaFilter.HEADER_CHAVE);
        request.addHeader(IdempotenciaFilter.HEADER_CHAVE, chaveControlada);
        Logger logger = (Logger) LoggerFactory.getLogger(IdempotenciaFilter.class);
        Level nivelAnterior = logger.getLevel();
        logger.setLevel(Level.INFO);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(nivelAnterior);
        }

        assertThat(appender.list).singleElement().satisfies(evento ->
                assertThat(evento.getFormattedMessage())
                        .contains(LogFingerprint.de(chaveControlada))
                        .doesNotContain("chave-secreta", "linha-forjada", "\r", "\n"));
    }

    @Test
    @DisplayName("erro de validação devolve a chave: quem corrige o formulário pode reenviar")
    void erroLiberaAChave() throws Exception {
        when(idempotenciaService.reservar(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoReserva.Prosseguir());

        var request = requisicao("POST", "/v1/descontos", "{}");

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> ((HttpServletResponse) res).setStatus(422));

        verify(idempotenciaService).liberar(CHAVE);
        verify(idempotenciaService, never()).concluir(anyString(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("exceção no handler também devolve a chave — reserva não pode vazar")
    void excecaoLiberaAChave() {
        when(idempotenciaService.reservar(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoReserva.Prosseguir());

        var request = requisicao("POST", "/v1/descontos", "{}");
        FilterChain explode = (req, res) -> {
            throw new IllegalStateException("falha no meio do handler");
        };

        try {
            filter.doFilter(request, new MockHttpServletResponse(), explode);
        } catch (Exception esperada) {
            // a exceção segue para o GlobalExceptionHandler; aqui só interessa a liberação
        }

        verify(idempotenciaService).liberar(CHAVE);
    }

    @Test
    @DisplayName("multipart não é bufferizado: 15 MB de anexo não viram cópia em memória")
    void multipartNaoEhDigerido() throws Exception {
        when(idempotenciaService.reservar(eq(CHAVE), eq(USUARIO), eq("POST"),
                eq("/v1/descontos/1/anexos"), eq(null)))
                .thenReturn(new ResultadoReserva.Prosseguir());

        var request = new MockHttpServletRequest("POST", "/v1/descontos/1/anexos");
        request.setRequestURI("/v1/descontos/1/anexos");
        request.setContentType("multipart/form-data; boundary=xyz");
        request.setContent("conteudo-binario".getBytes(StandardCharsets.UTF_8));
        request.addHeader(IdempotenciaFilter.HEADER_CHAVE, CHAVE);

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(req).as("multipart chega ao handler sem wrapper de buffer")
                            .isNotInstanceOf(CorpoBufferizadoRequest.class);
                    ((HttpServletResponse) res).setStatus(201);
                });

        verify(idempotenciaService).reservar(CHAVE, USUARIO, "POST", "/v1/descontos/1/anexos", null);
    }

    @Test
    @DisplayName("requisição sem usuário autenticado passa direto — a chave precisa de dono")
    void semUsuarioPassaDireto() throws Exception {
        SecurityContextHolder.clearContext();
        var request = requisicao("POST", "/v1/descontos", "{}");
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(idempotenciaService, never()).reservar(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("chaves diferentes para o mesmo payload são operações diferentes")
    void chavesDiferentesNaoColidem() throws Exception {
        when(idempotenciaService.reservar(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoReserva.Prosseguir());

        for (String chave : List.of("chave-a", "chave-b")) {
            var request = requisicao("POST", "/v1/descontos", "{}");
            request.removeHeader(IdempotenciaFilter.HEADER_CHAVE);
            request.addHeader(IdempotenciaFilter.HEADER_CHAVE, chave);
            filter.doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> ((HttpServletResponse) res).setStatus(201));
        }

        verify(idempotenciaService).reservar(eq("chave-a"), any(), anyString(), anyString(), anyString());
        verify(idempotenciaService).reservar(eq("chave-b"), any(), anyString(), anyString(), anyString());
        verify(idempotenciaService, times(2)).concluir(anyString(), anyInt(), any(), any(), anyString());
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpServletRequest requisicao(String metodo, String caminho, String corpo) {
        var request = new MockHttpServletRequest(metodo, caminho);
        request.setRequestURI(caminho);
        if (corpo != null) {
            request.setContentType("application/json");
            request.setContent(corpo.getBytes(StandardCharsets.UTF_8));
        }
        request.addHeader(IdempotenciaFilter.HEADER_CHAVE, CHAVE);
        return request;
    }

    private RegistroIdempotencia registroConcluido(int status, String contentType, String corpo) throws Exception {
        // Construtor protegido (só o JPA constrói de fora): o teste monta o registro como
        // o Hibernate montaria, sem abrir uma porta na entidade só para poder testá-la.
        var construtor = RegistroIdempotencia.class.getDeclaredConstructor();
        construtor.setAccessible(true);
        var registro = construtor.newInstance();
        ReflectionTestUtils.setField(registro, "statusHttp", status);
        ReflectionTestUtils.setField(registro, "contentType", contentType);
        ReflectionTestUtils.setField(registro, "corpoResposta", corpo);
        return registro;
    }
}
