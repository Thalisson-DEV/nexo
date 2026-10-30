package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.dto.ConcluirChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.ConverterConstatacaoEmAcaoRequest;
import com.nexo.app.modules.sst.application.dto.CriarExecucaoChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.EvidenciaGovernancaDownload;
import com.nexo.app.modules.sst.application.dto.EvidenciaGovernancaResponse;
import com.nexo.app.modules.sst.application.dto.ExecucaoChecklistGovernancaFilter;
import com.nexo.app.modules.sst.application.dto.ExecucaoChecklistGovernancaResponse;
import com.nexo.app.modules.sst.application.dto.SalvarRespostasChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.usecases.ConverterConstatacaoEmAcaoUseCase;
import com.nexo.app.modules.sst.application.usecases.EvidenciaGovernancaSstUseCases;
import com.nexo.app.modules.sst.application.usecases.ExecucaoChecklistGovernancaSstUseCases;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca/checklists/execucoes")
@RequiredArgsConstructor
@Tag(name = "SST - Governança - Execuções de checklist")
@SecurityRequirement(name = "bearerAuth")
public class ExecucaoChecklistGovernancaSstController {

    private final ExecucaoChecklistGovernancaSstUseCases useCases;
    private final ConverterConstatacaoEmAcaoUseCase converterUseCase;
    private final EvidenciaGovernancaSstUseCases evidenciaUseCases;

    @PostMapping
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Iniciar checklist predial ou preventivo")
    public ResponseEntity<ExecucaoChecklistGovernancaResponse> criar(
            @Valid @RequestBody CriarExecucaoChecklistGovernancaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                useCases.criar(request, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Consultar execução com respostas e constatações")
    public ResponseEntity<ExecucaoChecklistGovernancaResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Listar execuções de checklist")
    public ResponseEntity<PageResponse<ExecucaoChecklistGovernancaResponse>> listar(
            @ParameterObject ExecucaoChecklistGovernancaFilter filtro,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @PutMapping("/{id}/respostas")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Salvar rascunho de respostas")
    public ResponseEntity<ExecucaoChecklistGovernancaResponse> salvarRespostas(
            @PathVariable UUID id,
            @Valid @RequestBody SalvarRespostasChecklistGovernancaRequest request) {
        return ResponseEntity.ok(useCases.salvarRespostas(id, request));
    }

    @PostMapping("/{id}/conclusao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Concluir checklist após validar itens obrigatórios")
    public ResponseEntity<ExecucaoChecklistGovernancaResponse> concluir(
            @PathVariable UUID id,
            @Valid @RequestBody ConcluirChecklistGovernancaRequest request) {
        return ResponseEntity.ok(useCases.concluir(id, request.versaoEsperada()));
    }

    @PostMapping("/{id}/constatacoes/{idConstatacao}/acao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Converter constatação em ação após confirmação humana")
    public ResponseEntity<AcaoGovernancaSstResponse> converter(
            @PathVariable UUID id, @PathVariable UUID idConstatacao,
            @Valid @RequestBody ConverterConstatacaoEmAcaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                converterUseCase.converter(id, idConstatacao, request));
    }

    @PostMapping(value = "/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Anexar evidência privada ao checklist")
    public ResponseEntity<EvidenciaGovernancaResponse> anexar(
            @PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                evidenciaUseCases.anexarChecklist(id, arquivo, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/{id}/evidencias")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Listar evidências do checklist")
    public ResponseEntity<List<EvidenciaGovernancaResponse>> listarEvidencias(@PathVariable UUID id) {
        return ResponseEntity.ok(evidenciaUseCases.listarChecklist(id));
    }

    @GetMapping("/{id}/evidencias/{idEvidencia}/arquivo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Baixar evidência do checklist")
    public ResponseEntity<InputStreamResource> baixar(
            @PathVariable UUID id, @PathVariable UUID idEvidencia) {
        return download(evidenciaUseCases.baixarChecklist(id, idEvidencia));
    }

    @DeleteMapping("/{id}/evidencias/{idEvidencia}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Retirar evidência enquanto o checklist é rascunho")
    public ResponseEntity<Void> remover(
            @PathVariable UUID id, @PathVariable UUID idEvidencia) {
        evidenciaUseCases.removerChecklist(id, idEvidencia, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<InputStreamResource> download(EvidenciaGovernancaDownload arquivo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .contentLength(arquivo.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(new InputStreamResource(arquivo.conteudo()));
    }
}
