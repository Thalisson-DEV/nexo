package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.*;
import com.nexo.app.modules.rh.doc.ProcessoNegocioControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/processos-negocio")
@RequiredArgsConstructor
public class ProcessoNegocioController implements ProcessoNegocioControllerDoc {

    private final ProcessoNegocioUseCases processoNegocioUseCases;
    private final ImportarProcessoNegocioUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:processo-negocio:write')")
    public ResponseEntity<ProcessoNegocioResponse> criar(@Valid @RequestBody ProcessoNegocioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(processoNegocioUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:processo-negocio:write')")
    public ResponseEntity<ImportacaoProcessoNegocioResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:processo-negocio:write')")
    public ResponseEntity<ImportacaoProcessoNegocioResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoProcessoNegocioRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:processo-negocio:write')")
    public ResponseEntity<ProcessoNegocioResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody ProcessoNegocioRequest request) {
        return ResponseEntity.ok(processoNegocioUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:processo-negocio:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        processoNegocioUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:processo-negocio:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        processoNegocioUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:processo-negocio:read')")
    public ResponseEntity<ProcessoNegocioResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(processoNegocioUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:processo-negocio:read')")
    public ResponseEntity<PageResponse<ProcessoNegocioResponse>> listar(@ParameterObject ProcessoNegocioFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(processoNegocioUseCases.listar(filter, pageable));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:processo-negocio:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-processos-negocio.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
