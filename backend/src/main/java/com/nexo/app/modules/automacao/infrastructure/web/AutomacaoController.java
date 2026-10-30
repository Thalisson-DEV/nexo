package com.nexo.app.modules.automacao.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.automacao.application.dto.AprovacaoResponse;
import com.nexo.app.modules.automacao.application.dto.AutomacaoDetalheResponse;
import com.nexo.app.modules.automacao.application.dto.AutomacaoFilter;
import com.nexo.app.modules.automacao.application.dto.AutomacaoRequest;
import com.nexo.app.modules.automacao.application.dto.AutomacaoResumoResponse;
import com.nexo.app.modules.automacao.application.dto.ExecucaoDetalheResponse;
import com.nexo.app.modules.automacao.application.dto.ExecucaoResumoResponse;
import com.nexo.app.modules.automacao.application.dto.ExecutarRequest;
import com.nexo.app.modules.automacao.application.dto.OpcaoResponse;
import com.nexo.app.modules.automacao.application.dto.PreviaEmailRequest;
import com.nexo.app.modules.automacao.application.dto.PreviaEmailResponse;
import com.nexo.app.modules.automacao.application.usecases.PreviaEmailUseCases;
import com.nexo.app.modules.automacao.application.dto.RascunhoRequest;
import com.nexo.app.modules.automacao.application.dto.RascunhoResponse;
import com.nexo.app.modules.automacao.application.dto.RespostaAprovacaoRequest;
import com.nexo.app.modules.automacao.application.dto.StatusAutomacaoRequest;
import com.nexo.app.modules.automacao.application.dto.TransferirRequest;
import com.nexo.app.modules.automacao.application.dto.ValidacaoRequest;
import com.nexo.app.modules.automacao.application.dto.ValidacaoResponse;
import com.nexo.app.modules.automacao.application.usecases.AutomacaoUseCases;
import com.nexo.app.modules.automacao.application.usecases.RascunhoAutomacaoService;
import com.nexo.app.modules.automacao.doc.AutomacaoControllerDoc;
import com.nexo.app.modules.automacao.domain.ArquivoAutomacao;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/automacoes")
@RequiredArgsConstructor
public class AutomacaoController implements AutomacaoControllerDoc {

    private static final String LER = "hasAnyAuthority('automacao:read', 'automacao:manage')";
    private static final String GERIR = "hasAuthority('automacao:manage')";

    private final AutomacaoUseCases useCases;
    private final RascunhoAutomacaoService rascunhos;
    private final PreviaEmailUseCases previas;
    private final com.nexo.app.modules.automacao.application.usecases.ModelosAutomacao modelos;

    @Override
    @GetMapping("/modelos")
    @PreAuthorize(LER)
    public ResponseEntity<List<com.nexo.app.modules.automacao.application.usecases.ModelosAutomacao.Modelo>> modelos() {
        return ResponseEntity.ok(modelos.listar());
    }

    @Override
    @GetMapping("/catalogo")
    @PreAuthorize(LER)
    public ResponseEntity<Map<String, Object>> catalogo() {
        return ResponseEntity.ok(useCases.catalogo());
    }

    @Override
    @GetMapping
    @PreAuthorize(LER)
    public ResponseEntity<PageResponse<AutomacaoResumoResponse>> listar(@ParameterObject AutomacaoFilter filtro,
                                                                        @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize(LER)
    public ResponseEntity<AutomacaoDetalheResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscar(id));
    }

    @Override
    @PostMapping
    @PreAuthorize(GERIR)
    public ResponseEntity<AutomacaoDetalheResponse> criar(@Valid @RequestBody AutomacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize(GERIR)
    public ResponseEntity<AutomacaoDetalheResponse> salvar(@PathVariable UUID id, @Valid @RequestBody AutomacaoRequest request) {
        return ResponseEntity.ok(useCases.salvar(id, request));
    }

    @Override
    @PatchMapping("/{id}/status")
    @PreAuthorize(GERIR)
    public ResponseEntity<AutomacaoDetalheResponse> alterarStatus(@PathVariable UUID id, @Valid @RequestBody StatusAutomacaoRequest request) {
        return ResponseEntity.ok(useCases.alterarStatus(id, request.status()));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize(GERIR)
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        useCases.arquivar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{id}/duplicar")
    @PreAuthorize(GERIR)
    public ResponseEntity<AutomacaoDetalheResponse> duplicar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.duplicar(id));
    }

    @Override
    @PatchMapping("/{id}/responsavel")
    @PreAuthorize(GERIR)
    public ResponseEntity<AutomacaoDetalheResponse> transferir(@PathVariable UUID id, @Valid @RequestBody TransferirRequest request) {
        return ResponseEntity.ok(useCases.transferir(id, request.donoId()));
    }

    @Override
    @GetMapping("/{id}/versoes/{numero}")
    @PreAuthorize(LER)
    public ResponseEntity<Map<String, Object>> versao(@PathVariable UUID id, @PathVariable int numero) {
        return ResponseEntity.ok(useCases.versao(id, numero));
    }

    @Override
    @PostMapping("/validar")
    @PreAuthorize(LER)
    public ResponseEntity<ValidacaoResponse> validar(@RequestBody ValidacaoRequest request) {
        return ResponseEntity.ok(useCases.validar(request));
    }

    @Override
    @PostMapping("/previa-email")
    @PreAuthorize(LER)
    public ResponseEntity<PreviaEmailResponse> previaEmail(@RequestBody PreviaEmailRequest request) {
        return ResponseEntity.ok(previas.previa(request));
    }

    @Override
    @PostMapping("/{id}/executar")
    @PreAuthorize(GERIR)
    public ResponseEntity<ExecucaoResumoResponse> executar(@PathVariable UUID id, @RequestBody(required = false) ExecutarRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(useCases.executar(id, request));
    }

    @Override
    @GetMapping("/{id}/execucoes")
    @PreAuthorize(LER)
    public ResponseEntity<PageResponse<ExecucaoResumoResponse>> execucoes(@PathVariable UUID id, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.execucoes(id, pageable));
    }

    @Override
    @GetMapping("/{id}/execucoes/{execucaoId}")
    @PreAuthorize(LER)
    public ResponseEntity<ExecucaoDetalheResponse> execucao(@PathVariable UUID id, @PathVariable UUID execucaoId) {
        return ResponseEntity.ok(useCases.execucao(id, execucaoId));
    }

    @Override
    @PostMapping("/{id}/execucoes/{execucaoId}/cancelar")
    @PreAuthorize(GERIR)
    public ResponseEntity<Void> cancelar(@PathVariable UUID id, @PathVariable UUID execucaoId) {
        useCases.cancelarExecucao(id, execucaoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{id}/execucoes/{execucaoId}/reenviar")
    @PreAuthorize(GERIR)
    public ResponseEntity<ExecucaoResumoResponse> reenviar(@PathVariable UUID id, @PathVariable UUID execucaoId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(useCases.reenviar(id, execucaoId));
    }

    @Override
    @GetMapping("/arquivos/{arquivoId}")
    @PreAuthorize(LER)
    public ResponseEntity<byte[]> arquivo(@PathVariable UUID arquivoId) {
        ArquivoAutomacao arquivo = useCases.arquivo(arquivoId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(arquivo.getNome(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(arquivo.getTipoConteudo()))
                .body(arquivo.getConteudo());
    }

    @Override
    @PostMapping("/rascunho")
    @PreAuthorize(GERIR)
    public ResponseEntity<RascunhoResponse> rascunho(@Valid @RequestBody RascunhoRequest request) {
        return ResponseEntity.ok(rascunhos.rascunhar(request.pedido(), request.automacaoId(), SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @GetMapping("/opcoes/{tipo}")
    @PreAuthorize(GERIR)
    public ResponseEntity<List<OpcaoResponse>> opcoes(@PathVariable String tipo, @RequestParam(required = false) String busca) {
        return ResponseEntity.ok(useCases.opcoes(tipo, busca));
    }

    @Override
    @GetMapping("/rotulos/{tipo}")
    @PreAuthorize(LER)
    public ResponseEntity<List<OpcaoResponse>> rotulos(@PathVariable String tipo, @RequestParam(required = false) List<String> valores) {
        return ResponseEntity.ok(useCases.rotulos(tipo, valores));
    }

    @Override
    @GetMapping("/aprovacoes/minhas")
    public ResponseEntity<List<AprovacaoResponse>> minhasAprovacoes() {
        return ResponseEntity.ok(useCases.minhasAprovacoes());
    }

    @Override
    @GetMapping("/aprovacoes/{id}")
    public ResponseEntity<AprovacaoResponse> aprovacao(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarAprovacao(id));
    }

    @Override
    @PostMapping("/aprovacoes/{id}/resposta")
    public ResponseEntity<AprovacaoResponse> responder(@PathVariable UUID id, @Valid @RequestBody RespostaAprovacaoRequest request) {
        return ResponseEntity.ok(useCases.responder(id, request));
    }
}
