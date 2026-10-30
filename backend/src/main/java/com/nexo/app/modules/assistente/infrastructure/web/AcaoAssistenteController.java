package com.nexo.app.modules.assistente.infrastructure.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.modules.assistente.application.AcoesPendentesService;
import com.nexo.app.modules.assistente.application.cadastro.Opcao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Cartoes de confirmacao do Copiloto: o usuario revisa, confirma ou cancela o que o assistente preparou.
 *
 * <p>As previas sao arvores JSON do Jackson 2 (as mesmas do agente); o conversor HTTP do Spring usa o Jackson 3,
 * que nao as conhece. Por isso entrada e saida sao serializadas aqui, com o mesmo mapper do agente.</p>
 */
@RestController
@RequestMapping("/v1/assistente")
@PreAuthorize("hasAuthority('assistente:chat:use')")
@Tag(name = "Copiloto - ações", description = "Confirmação das ações preparadas pelo Copiloto")
public class AcaoAssistenteController {

    private final AcoesPendentesService service;
    private final ObjectMapper mapper;

    public AcaoAssistenteController(AcoesPendentesService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping(value = "/conversas/{conversaId}/acoes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Estado dos cartões de confirmação da conversa")
    public ResponseEntity<String> listar(@PathVariable UUID conversaId) {
        return json(service.listar(conversaId));
    }

    /** Corpo: {"alteracoes": [{registro, caminho, valor} | {registro, remover}]}. */
    @PostMapping(value = "/acoes/{acaoId}/revisao", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Aplica ajustes ao cartão sem gravar")
    public ResponseEntity<String> revisar(@PathVariable UUID acaoId, @RequestBody(required = false) String corpo) {
        return json(service.revisar(acaoId, alteracoes(corpo)));
    }

    @PostMapping(value = "/acoes/{acaoId}/confirmacao", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirma o cartão e grava pelos casos de uso do módulo")
    public ResponseEntity<String> confirmar(@PathVariable UUID acaoId, @RequestBody(required = false) String corpo) {
        return json(service.confirmar(acaoId, alteracoes(corpo)));
    }

    @PostMapping(value = "/acoes/{acaoId}/cancelamento", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancela o cartão sem gravar nada")
    public ResponseEntity<String> rejeitar(@PathVariable UUID acaoId) {
        return json(service.rejeitar(acaoId));
    }

    @GetMapping("/cadastros/referencias/{tipo}")
    @Operation(summary = "Busca registros de um cadastro referenciado pelo cartão")
    public ResponseEntity<List<Opcao>> referencias(@PathVariable String tipo, @RequestParam(required = false) String termo) {
        return ResponseEntity.ok(service.buscarReferencia(tipo, termo));
    }

    private JsonNode alteracoes(String corpo) {
        if (corpo == null || corpo.isBlank()) return null;
        try {
            JsonNode no = mapper.readTree(corpo);
            JsonNode alteracoes = no.isObject() ? no.get("alteracoes") : no;
            return alteracoes == null || alteracoes.isNull() ? null : alteracoes;
        } catch (JsonProcessingException exception) {
            throw new InvarianteVioladaException("Os ajustes do cartão não estão no formato esperado.");
        }
    }

    private ResponseEntity<String> json(Object valor) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(valor));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao montar a resposta do cartão", exception);
        }
    }
}
