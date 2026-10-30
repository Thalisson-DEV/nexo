package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.ModeloChecklistGovernancaFilter;
import com.nexo.app.modules.sst.application.dto.ModeloChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.dto.ModeloChecklistGovernancaResponse;
import com.nexo.app.modules.sst.application.dto.DuplicarModeloChecklistGovernancaRequest;
import com.nexo.app.modules.sst.application.usecases.ModeloChecklistGovernancaSstUseCases;
import com.nexo.app.shared.application.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca/modelos-checklist")
@RequiredArgsConstructor
@Tag(name = "SST - Governança - Modelos de checklist")
@SecurityRequirement(name = "bearerAuth")
public class ModeloChecklistGovernancaSstController {

    private final ModeloChecklistGovernancaSstUseCases useCases;

    @PostMapping
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Publicar modelo de checklist para CIPA ou Brigada")
    public ResponseEntity<ModeloChecklistGovernancaResponse> criar(
            @Valid @RequestBody ModeloChecklistGovernancaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Consultar modelo de checklist")
    public ResponseEntity<ModeloChecklistGovernancaResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    @Operation(summary = "Listar modelos de checklist")
    public ResponseEntity<PageResponse<ModeloChecklistGovernancaResponse>> listar(
            @ParameterObject ModeloChecklistGovernancaFilter filtro,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Atualizar metadados do modelo")
    public ResponseEntity<ModeloChecklistGovernancaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody ModeloChecklistGovernancaRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @PostMapping("/{id}/duplicacao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Duplicar modelo e sua estrutura para criar nova versão")
    public ResponseEntity<ModeloChecklistGovernancaResponse> duplicar(
            @PathVariable UUID id,
            @Valid @RequestBody DuplicarModeloChecklistGovernancaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.duplicar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Desativar modelo")
    public ResponseEntity<Void> desativar(
            @PathVariable UUID id, @RequestParam Long versaoEsperada) {
        useCases.desativar(id, versaoEsperada);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    @Operation(summary = "Reativar modelo")
    public ResponseEntity<Void> ativar(
            @PathVariable UUID id, @RequestParam Long versaoEsperada) {
        useCases.ativar(id, versaoEsperada);
        return ResponseEntity.noContent().build();
    }
}
