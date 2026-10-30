package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.BaseOperacionalUseCases;
import com.nexo.app.modules.rh.application.usecases.ImportarBasesOperacionaisUseCase;
import com.nexo.app.modules.rh.doc.BaseOperacionalControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/bases-operacionais")
@RequiredArgsConstructor
public class BaseOperacionalController implements BaseOperacionalControllerDoc {

    private final BaseOperacionalUseCases baseUseCases;
    private final ImportarBasesOperacionaisUseCase importarUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('rh:base-operacional:write')")
    public ResponseEntity<BaseOperacionalResponse> criar(@Valid @RequestBody BaseOperacionalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(baseUseCases.criar(request));
    }

    @Override
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:base-operacional:write')")
    public ResponseEntity<ImportacaoBaseOperacionalResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:base-operacional:write')")
    public ResponseEntity<ImportacaoBaseOperacionalResponse> confirmarImportacao(@Valid @RequestBody ConfirmarImportacaoBaseRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:base-operacional:write')")
    public ResponseEntity<BaseOperacionalResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody BaseOperacionalRequest request) {
        return ResponseEntity.ok(baseUseCases.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:base-operacional:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        baseUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:base-operacional:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        baseUseCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:base-operacional:read')")
    public ResponseEntity<BaseOperacionalResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(baseUseCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('rh:base-operacional:read')")
    public ResponseEntity<PageResponse<BaseOperacionalResponse>> listar(@ParameterObject BaseOperacionalFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(baseUseCases.listar(filter, pageable));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:base-operacional:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-bases-operacionais.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
