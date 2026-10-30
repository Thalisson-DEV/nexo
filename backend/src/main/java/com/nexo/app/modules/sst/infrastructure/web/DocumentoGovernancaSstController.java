package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.*;
import com.nexo.app.modules.sst.application.usecases.CategoriaDocumentoSstUseCases;
import com.nexo.app.modules.sst.application.usecases.DocumentoSstUseCases;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca")
@RequiredArgsConstructor
public class DocumentoGovernancaSstController {
    private final CategoriaDocumentoSstUseCases categorias;
    private final DocumentoSstUseCases documentos;

    @PostMapping("/categorias-documento")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<CategoriaDocumentoSstResponse> criarCategoria(
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody CategoriaDocumentoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                categorias.criar(request, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/categorias-documento/{id}/nova-versao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<CategoriaDocumentoSstResponse> versionarCategoria(
            @RequestHeader("Idempotency-Key") String chave, @PathVariable UUID id,
            @Valid @RequestBody CategoriaDocumentoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                categorias.versionar(id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/categorias-documento/{id}/confirmacao")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<CategoriaDocumentoSstResponse> confirmarCategoria(@PathVariable UUID id) {
        return ResponseEntity.ok(categorias.confirmar(id, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/categorias-documento/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<CategoriaDocumentoSstResponse> buscarCategoria(@PathVariable UUID id) {
        return ResponseEntity.ok(categorias.buscarPorId(id));
    }

    @GetMapping("/categorias-documento")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<CategoriaDocumentoSstResponse>> listarCategorias(
            @ParameterObject CategoriaDocumentoSstFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(categorias.listar(filtro, pageable));
    }

    @PostMapping(value = "/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<DocumentoSstResponse> enviar(
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestPart("dados") DocumentoSstMetadataRequest dados,
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestParam(required = false) UUID idDocumentoSubstituido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentos.enviar(
                dados, arquivo, idDocumentoSubstituido, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/documentos")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<DocumentoSstResponse>> listar(
            @ParameterObject DocumentoSstFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(documentos.listar(filtro, pageable));
    }

    @GetMapping("/documentos/resumo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<ResumoDocumentosSstResponse> resumo(@ParameterObject DocumentoSstFilter filtro) {
        return ResponseEntity.ok(documentos.resumo(filtro));
    }

    @GetMapping("/documentos/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<DocumentoSstResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(documentos.buscarPorId(id));
    }

    @GetMapping("/documentos/{id}/historico")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<DocumentoSstResponse>> historico(@PathVariable UUID id) {
        return ResponseEntity.ok(documentos.historico(id));
    }

    @PostMapping("/documentos/{id}/conferencia")
    @PreAuthorize("hasAuthority('sst:documento:conferir')")
    public ResponseEntity<DocumentoSstResponse> conferir(
            @RequestHeader("Idempotency-Key") String chave, @PathVariable UUID id,
            @Valid @RequestBody ConferenciaDocumentoSstRequest request) {
        UUID ator = SecurityUtils.getUsuarioLogadoId();
        return ResponseEntity.ok(request.decisao() == ConferenciaDocumentoSstRequest.Decisao.APROVAR
                ? documentos.aprovar(id, ator) : documentos.rejeitar(id, request.motivo(), ator));
    }

    @PostMapping("/documentos/{id}/arquivamento")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<DocumentoSstResponse> arquivar(
            @PathVariable UUID id, @Valid @RequestBody ArquivarDocumentoSstRequest request) {
        return ResponseEntity.ok(documentos.arquivar(
                id, request.motivo(), SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping("/documentos/{id}/arquivo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<InputStreamResource> baixar(@PathVariable UUID id) {
        DocumentoSstDownload arquivo = documentos.baixar(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(arquivo.contentType()))
                .contentLength(arquivo.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(arquivo.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(arquivo.conteudo()));
    }
}
