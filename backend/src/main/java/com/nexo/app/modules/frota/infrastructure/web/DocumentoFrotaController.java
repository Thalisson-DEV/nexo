package com.nexo.app.modules.frota.infrastructure.web;

import com.nexo.app.modules.frota.application.dto.DocumentoFrotaFilter;
import com.nexo.app.modules.frota.application.dto.DocumentoFrotaRequest;
import com.nexo.app.modules.frota.application.dto.DocumentoFrotaResponse;
import com.nexo.app.modules.frota.application.usecases.DocumentoFrotaUseCases;
import com.nexo.app.modules.frota.doc.DocumentoFrotaControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
import java.util.UUID;

@RestController
@RequestMapping("/v1/frota/documentos")
@RequiredArgsConstructor
public class DocumentoFrotaController implements DocumentoFrotaControllerDoc {

    private final DocumentoFrotaUseCases documentos;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('frota:documento:read')")
    public ResponseEntity<PageResponse<DocumentoFrotaResponse>> listar(@ParameterObject DocumentoFrotaFilter filtro,
                                                                      @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(documentos.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('frota:documento:read')")
    public ResponseEntity<DocumentoFrotaResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(documentos.buscarPorId(id));
    }

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('frota:documento:write')")
    public ResponseEntity<DocumentoFrotaResponse> criar(@Valid @RequestPart("dados") DocumentoFrotaRequest dados,
                                                        @RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentos.criar(dados, arquivo));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('frota:documento:write')")
    public ResponseEntity<DocumentoFrotaResponse> atualizar(@PathVariable UUID id,
                                                            @Valid @RequestBody DocumentoFrotaRequest request) {
        return ResponseEntity.ok(documentos.atualizar(id, request));
    }

    @Override
    @PostMapping(value = "/{id}/arquivo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('frota:documento:write')")
    public ResponseEntity<DocumentoFrotaResponse> anexar(@PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(documentos.anexar(id, arquivo));
    }

    @Override
    @GetMapping("/{id}/arquivo")
    @PreAuthorize("hasAuthority('frota:documento:read')")
    public ResponseEntity<Resource> baixar(@PathVariable UUID id) {
        var arquivo = documentos.baixar(id);
        String disposition = ContentDisposition.inline()
                .filename(arquivo.nome(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .contentLength(arquivo.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new InputStreamResource(arquivo.conteudo()));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('frota:documento:write')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        documentos.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
