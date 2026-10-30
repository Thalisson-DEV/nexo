package com.nexo.app.modules.desconto.infrastructure.web;

import com.nexo.app.modules.desconto.application.dto.DescontoBaixaRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoFilter;
import com.nexo.app.modules.desconto.application.dto.DescontoItemRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoResponse;
import com.nexo.app.modules.desconto.application.dto.DescontoValidacaoRequest;
import com.nexo.app.modules.desconto.application.usecases.DescontoUseCases;
import com.nexo.app.modules.desconto.doc.DescontoDoc;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/v1/descontos")
@RequiredArgsConstructor
public class DescontoController implements DescontoDoc {

    private final DescontoUseCases useCases;
    private final com.nexo.app.modules.desconto.application.usecases.ExportarDescontosUseCase exportarDescontosUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('desconto:solicitar')")
    public ResponseEntity<DescontoResponse> solicitar(@Valid @RequestBody DescontoRequest request, @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.solicitar(request, usuario.getId()));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:editar')")
    public ResponseEntity<DescontoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody DescontoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PostMapping("/{id}/itens")
    @PreAuthorize("hasAuthority('desconto:editar')")
    public ResponseEntity<Void> adicionarItem(@PathVariable UUID id, @Valid @RequestBody DescontoItemRequest request) {
        useCases.adicionarItem(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    @PutMapping("/{id}/itens/{itemId}")
    @PreAuthorize("hasAuthority('desconto:editar')")
    public ResponseEntity<Void> atualizarItem(@PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody DescontoItemRequest request) {
        useCases.atualizarItem(id, itemId, request);
        return ResponseEntity.ok().build();
    }

    @Override
    @DeleteMapping("/{id}/itens/{itemId}")
    @PreAuthorize("hasAuthority('desconto:editar')")
    public ResponseEntity<Void> removerItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        useCases.removerItem(id, itemId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping(value = "/{id}/anexos", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('desconto:anexar')")
    public ResponseEntity<Void> adicionarAnexo(@PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo) {
        useCases.adicionarAnexo(id, arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    @DeleteMapping("/{id}/anexos/{anexoId}")
    @PreAuthorize("hasAuthority('desconto:anexar')")
    public ResponseEntity<Void> removerAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        useCases.removerAnexo(id, anexoId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}/anexos/{anexoId}/download")
    @PreAuthorize("hasAuthority('desconto:read')")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        var anexo = useCases.baixarAnexo(id, anexoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(anexo.contentType()))
                .contentLength(anexo.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + anexo.nomeArquivo() + "\"")
                .body(new InputStreamResource(anexo.conteudo()));
    }

    @Override
    @PatchMapping("/{id}/validar")
    @PreAuthorize("hasAuthority('desconto:validar')")
    public ResponseEntity<Void> validar(@PathVariable UUID id, @Valid @RequestBody DescontoValidacaoRequest request, @AuthenticationPrincipal Usuario usuario) {
        useCases.validar(id, usuario.getId(), request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PatchMapping("/{id}/baixar")
    @PreAuthorize("hasAuthority('desconto:baixar')")
    public ResponseEntity<Void> baixar(@PathVariable UUID id, @Valid @RequestBody DescontoBaixaRequest request, @AuthenticationPrincipal Usuario usuario) {
        useCases.baixar(id, usuario.getId(), request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PatchMapping("/{id}/rejeitar")
    @PreAuthorize("hasAuthority('desconto:rejeitar')")
    public ResponseEntity<Void> rejeitar(@PathVariable UUID id, @Valid @RequestBody com.nexo.app.modules.desconto.application.dto.DescontoRejeicaoRequest request, @AuthenticationPrincipal Usuario usuario) {
        useCases.rejeitar(id, usuario.getId(), request);
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:read')")
    public ResponseEntity<DescontoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('desconto:read')")
    public ResponseEntity<PageResponse<DescontoResponse>> listar(@ParameterObject DescontoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/exportar/resumo")
    @PreAuthorize("hasAuthority('desconto:read')")
    public ResponseEntity<StreamingResponseBody> exportarResumo(@ParameterObject DescontoFilter filter) {
        StreamingResponseBody responseBody = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF'); // BOM
                exportarDescontosUseCase.exportarResumo(filter, writer);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"descontos_resumo.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(responseBody);
    }

    @Override
    @GetMapping("/exportar/detalhado")
    @PreAuthorize("hasAuthority('desconto:read')")
    public ResponseEntity<StreamingResponseBody> exportarDetalhado(@ParameterObject DescontoFilter filter) {
        StreamingResponseBody responseBody = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF'); // BOM
                exportarDescontosUseCase.exportarDetalhado(filter, writer);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"descontos_detalhado.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(responseBody);
    }
}
