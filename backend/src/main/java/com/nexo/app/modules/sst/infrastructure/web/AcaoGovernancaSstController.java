package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.dto.CancelarAcaoGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.ConcluirAcaoGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.EvidenciaGovernancaDownload;
import com.nexo.app.modules.sst.application.dto.EvidenciaGovernancaResponse;
import com.nexo.app.modules.sst.application.dto.VersaoGovernancaRequest;
import com.nexo.app.modules.sst.application.usecases.AcaoGovernancaSstUseCases;
import com.nexo.app.modules.sst.application.usecases.EvidenciaGovernancaSstUseCases;
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
@RequestMapping("/v1/sst/governanca/acoes")
@RequiredArgsConstructor
@Tag(name = "SST - Governança - Ações", description = "Plano de ação comum da CIPA e da Brigada")
@SecurityRequirement(name = "bearerAuth")
public class AcaoGovernancaSstController {

    private final AcaoGovernancaSstUseCases useCases;
    private final EvidenciaGovernancaSstUseCases evidenciaUseCases;

    @PostMapping
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Criar ação de governança")
    public ResponseEntity<AcaoGovernancaSstResponse> criar(
            @Valid @RequestBody AcaoGovernancaSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Consultar ação")
    public ResponseEntity<AcaoGovernancaSstResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Listar ações com filtros e paginação")
    public ResponseEntity<PageResponse<AcaoGovernancaSstResponse>> listar(
            @ParameterObject AcaoGovernancaSstFilter filtro,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Atualizar ação ainda aberta")
    public ResponseEntity<AcaoGovernancaSstResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody AcaoGovernancaSstRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @PostMapping("/{id}/inicio")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Iniciar execução da ação")
    public ResponseEntity<AcaoGovernancaSstResponse> iniciar(
            @PathVariable UUID id, @Valid @RequestBody VersaoGovernancaRequest request) {
        return ResponseEntity.ok(useCases.iniciar(id, request.versaoEsperada()));
    }

    @PostMapping("/{id}/conclusao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Concluir ação com resultado")
    public ResponseEntity<AcaoGovernancaSstResponse> concluir(
            @PathVariable UUID id, @Valid @RequestBody ConcluirAcaoGovernancaRequest request) {
        return ResponseEntity.ok(useCases.concluir(id, request.resultado(),
                request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    @Operation(summary = "Cancelar ação de forma motivada")
    public ResponseEntity<AcaoGovernancaSstResponse> cancelar(
            @PathVariable UUID id, @Valid @RequestBody CancelarAcaoGovernancaRequest request) {
        return ResponseEntity.ok(useCases.cancelar(id, request.motivo(),
                request.versaoEsperada(), SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping(value = "/{id}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Anexar evidência privada à ação")
    public ResponseEntity<EvidenciaGovernancaResponse> anexar(
            @PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                evidenciaUseCases.anexarAcao(id, arquivo, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/{id}/evidencias")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Listar evidências da ação")
    public ResponseEntity<List<EvidenciaGovernancaResponse>> listarEvidencias(@PathVariable UUID id) {
        return ResponseEntity.ok(evidenciaUseCases.listarAcao(id));
    }

    @GetMapping("/{id}/evidencias/{idEvidencia}/arquivo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Baixar evidência da ação")
    public ResponseEntity<InputStreamResource> baixar(
            @PathVariable UUID id, @PathVariable UUID idEvidencia) {
        return download(evidenciaUseCases.baixarAcao(id, idEvidencia));
    }

    @DeleteMapping("/{id}/evidencias/{idEvidencia}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Retirar evidência antes do encerramento da ação")
    public ResponseEntity<Void> remover(
            @PathVariable UUID id, @PathVariable UUID idEvidencia) {
        evidenciaUseCases.removerAcao(id, idEvidencia, SecurityUtils.getUsuarioLogadoId());
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
