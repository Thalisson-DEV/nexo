package com.nexo.app.modules.assistente.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.AssistenteService;
import com.nexo.app.modules.assistente.application.agent.AgentRun;
import com.nexo.app.modules.assistente.application.agent.AgentRunner;
import com.nexo.app.modules.assistente.application.artifacts.ArtifactService;
import com.nexo.app.modules.assistente.application.dto.*;
import com.nexo.app.modules.assistente.doc.AssistenteControllerDoc;
import com.nexo.app.modules.assistente.domain.MensagemAssistente;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API do workspace do Copiloto. O streaming e escrito de forma sincrona na resposta (thread virtual da
 * requisicao): evita o despacho assincrono do Servlet, no qual o contexto de seguranca nao e propagado.
 * Cabecalhos desativam buffer de proxy (nginx) e compressao para entregar cada pacote imediatamente.
 */
@RestController
@RequestMapping("/v1/assistente")
@PreAuthorize("hasAuthority('assistente:chat:use')")
public class AssistenteController implements AssistenteControllerDoc {

    private static final byte[] HEARTBEAT = "{\"placement\":{\"turnIndex\":0,\"tabIndex\":0},\"obj\":{\"type\":\"heartbeat\"}}\n"
            .getBytes(StandardCharsets.UTF_8);
    private static final long ESPERA_MS = 15_000;
    private static final Set<String> INLINE_SEGUROS = Set.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf");

    private final AssistenteService service;
    private final ObjectMapper objectMapper;

    public AssistenteController(AssistenteService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    @GetMapping("/conversas")
    public ResponseEntity<PageResponse<ConversaResumoResponse>> listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "30") int tamanho,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String painel) {
        return ResponseEntity.ok(service.listar(pagina, tamanho, busca, painel));
    }

    @Override
    @PostMapping("/conversas")
    public ResponseEntity<ConversaResumoResponse> criar(@Valid @RequestBody(required = false) CriarConversaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request));
    }

    @Override
    @GetMapping("/conversas/{conversaId}")
    public ResponseEntity<ConversaDetalheResponse> buscar(@PathVariable UUID conversaId) {
        return ResponseEntity.ok(service.buscar(conversaId));
    }

    @Override
    @PatchMapping("/conversas/{conversaId}")
    public ResponseEntity<ConversaResumoResponse> renomear(@PathVariable UUID conversaId,
            @Valid @RequestBody RenomearConversaRequest request) {
        return ResponseEntity.ok(service.renomear(conversaId, request));
    }

    @Override
    @DeleteMapping("/conversas/{conversaId}")
    public ResponseEntity<Void> excluir(@PathVariable UUID conversaId) {
        service.excluir(conversaId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/conversas/{conversaId}/mensagens/stream")
    public void enviar(@PathVariable UUID conversaId, @Valid @RequestBody EnviarMensagemRequest request,
            HttpServletResponse response) throws IOException {
        AgentRunner.Inicio inicio = service.enviar(conversaId, request);
        if (inicio.execucao() != null) {
            transmitir(inicio.execucao(), 0, response);
        } else {
            reproduzir(inicio.concluida(), response);
        }
    }

    @Override
    @GetMapping("/conversas/{conversaId}/stream")
    public void retomar(@PathVariable UUID conversaId, @RequestParam(defaultValue = "0") int cursor,
            HttpServletResponse response) throws IOException {
        AgentRun run = service.execucao(conversaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhuma execução recente nesta conversa"));
        transmitir(run, cursor, response);
    }

    @Override
    @PostMapping("/conversas/{conversaId}/parar")
    public ResponseEntity<Map<String, Object>> parar(@PathVariable UUID conversaId) {
        return ResponseEntity.ok(Map.of("interrompida", service.parar(conversaId)));
    }

    @Override
    @GetMapping("/conversas/{conversaId}/mensagens/{mensagemId}")
    public ResponseEntity<MensagemResponse> mensagem(@PathVariable UUID conversaId, @PathVariable UUID mensagemId) {
        return ResponseEntity.ok(service.mensagem(conversaId, mensagemId));
    }

    @Override
    @GetMapping("/conversas/{conversaId}/artefatos")
    public ResponseEntity<List<ArtifactService.ArtefatoView>> artefatos(@PathVariable UUID conversaId) {
        return ResponseEntity.ok(service.artefatos(conversaId));
    }

    @Override
    @GetMapping("/conversas/{conversaId}/artefatos/{codigo}")
    public ResponseEntity<ArtifactService.ArtefatoView> artefato(@PathVariable UUID conversaId, @PathVariable String codigo) {
        return ResponseEntity.ok(service.artefato(conversaId, codigo));
    }

    @Override
    @GetMapping("/conversas/{conversaId}/resultados/{codigo}")
    public ResponseEntity<Map<String, Object>> resultado(@PathVariable UUID conversaId, @PathVariable String codigo,
            @RequestParam(defaultValue = "200") int limite) {
        return ResponseEntity.ok(service.resultado(conversaId, codigo, limite));
    }

    @Override
    @PostMapping(value = "/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArquivoAssistenteResponse> enviarArquivo(@RequestParam("arquivo") MultipartFile arquivo)
            throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.enviarArquivo(arquivo.getOriginalFilename(), arquivo.getContentType(), arquivo.getBytes()));
    }

    @Override
    @GetMapping("/arquivos/{arquivoId}/conteudo")
    public ResponseEntity<byte[]> baixarArquivo(@PathVariable UUID arquivoId,
            @RequestParam(defaultValue = "true") boolean download) {
        AssistenteService.ArquivoBaixado arquivo = service.baixarArquivo(arquivoId);
        boolean inline = !download && INLINE_SEGUROS.contains(arquivo.tipoConteudo());
        ContentDisposition disposicao = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(arquivo.nome(), StandardCharsets.UTF_8).build();
        MediaType tipo = inline || INLINE_SEGUROS.contains(arquivo.tipoConteudo())
                ? MediaType.parseMediaType(arquivo.tipoConteudo()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(arquivo.conteudo());
    }

    @Override
    @DeleteMapping("/arquivos/{arquivoId}")
    public ResponseEntity<Void> removerArquivo(@PathVariable UUID arquivoId) {
        service.removerArquivo(arquivoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/capacidades")
    public ResponseEntity<CapacidadesResponse> capacidades() {
        return ResponseEntity.ok(service.capacidades());
    }

    // ------------------------------------------------------------------------------------ streaming

    private void transmitir(AgentRun run, int cursor, HttpServletResponse response) throws IOException {
        prepararStream(response);
        ServletOutputStream saida = response.getOutputStream();
        int posicao = Math.max(0, cursor);
        try {
            saida.flush();
            while (true) {
                List<String> linhas = run.readFrom(posicao, ESPERA_MS);
                if (linhas.isEmpty()) {
                    if (run.isFinished()) break;
                    saida.write(HEARTBEAT);
                    saida.flush();
                    continue;
                }
                for (String linha : linhas) {
                    saida.write(linha.getBytes(StandardCharsets.UTF_8));
                    saida.write('\n');
                }
                posicao += linhas.size();
                saida.flush();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            // Cliente desconectou: a execucao continua e pode ser retomada pelo cursor.
        }
    }

    /** Pergunta repetida ja respondida: reenvia a timeline persistida e o pacote final. */
    private void reproduzir(MensagemAssistente mensagem, HttpServletResponse response) throws IOException {
        prepararStream(response);
        ServletOutputStream saida = response.getOutputStream();
        if (mensagem.getPacotesJson() != null && !mensagem.getPacotesJson().isBlank()) {
            JsonNode pacotes = objectMapper.readTree(mensagem.getPacotesJson());
            for (JsonNode pacote : pacotes) {
                saida.write(objectMapper.writeValueAsBytes(pacote));
                saida.write('\n');
            }
        }
        Map<String, Object> stop = Map.of("placement", Map.of("turnIndex", 0, "tabIndex", 0),
                "obj", Map.of("type", "stop", "status", mensagem.getStatus().name(), "mensagemId", mensagem.getId().toString(),
                        "repetida", true));
        saida.write(objectMapper.writeValueAsBytes(stop));
        saida.write('\n');
        saida.flush();
    }

    private static void prepararStream(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/x-ndjson");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
