package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.SupervisorResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.ImportarSupervisoresUseCase;
import com.nexo.app.modules.rh.application.usecases.SupervisorUseCases;
import com.nexo.app.modules.rh.doc.SupervisorControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/rh/supervisores")
@RequiredArgsConstructor
public class SupervisorController implements SupervisorControllerDoc {

    private final SupervisorUseCases supervisorUseCases;
    private final ImportarSupervisoresUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:supervisor:write')")
    public ResponseEntity<SupervisorResponse> criar(@RequestBody @Valid SupervisorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supervisorUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:supervisor:write')")
    public ResponseEntity<ImportacaoSupervisorResponse> validarImportacao(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:supervisor:write')")
    public ResponseEntity<ImportacaoSupervisorResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoSupervisorRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:supervisor:write')")
    public ResponseEntity<SupervisorResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid SupervisorRequest request) {
        return ResponseEntity.ok(supervisorUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:supervisor:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        supervisorUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:supervisor:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        supervisorUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:supervisor:read')")
    public ResponseEntity<SupervisorResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(supervisorUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:supervisor:read')")
    public ResponseEntity<PageResponse<SupervisorResponse>> listar(@ParameterObject SupervisorFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(supervisorUseCases.listar(filter, pageable));
    }

    @GetMapping("/min")
    @PreAuthorize("hasAuthority('rh:supervisor:read')")
    public ResponseEntity<List<SupervisorMinResponse>> listarAtivosMin() {
        return ResponseEntity.ok(supervisorUseCases.listarAtivosMin());
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:supervisor:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-supervisores.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
