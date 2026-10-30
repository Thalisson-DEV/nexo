package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.EquipeMembroResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.*;
import com.nexo.app.modules.rh.doc.EquipeControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/equipes")
@RequiredArgsConstructor
public class EquipeController implements EquipeControllerDoc {

    private final EquipeUseCases equipeUseCases;
    private final MembroEquipeUseCase MembroUseCase;
    private final ImportarEquipesUseCase importarEquipesUseCase;

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<ImportacaoEquipeResponse> validarImportacao(MultipartFile arquivo) {
        return ResponseEntity.ok(importarEquipesUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<ImportacaoEquipeResponse> confirmarImportacao(ConfirmarImportacaoEquipeRequest request) {
        return ResponseEntity.ok(importarEquipesUseCase.confirmarImportacao(request));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<EquipeResponse> criar(@RequestBody @Valid EquipeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(equipeUseCases.criar(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<EquipeResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid EquipeRequest request) {
        return ResponseEntity.ok(equipeUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        equipeUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:equipe:read')")
    public ResponseEntity<EquipeResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(equipeUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:equipe:read')")
    public ResponseEntity<PageResponse<EquipeResponse>> listar(EquipeFilter filter, Pageable pageable) {
        return ResponseEntity.ok(equipeUseCases.listar(filter, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        equipeUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/membros")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<Void> alocarMembro(@PathVariable UUID id, @RequestBody @Valid EquipeMembroRequest request) {
        MembroUseCase.alocarMembro(id, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/membros/batch")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<Void> alocarMembros(@PathVariable UUID id, @RequestBody @Valid List<EquipeMembroRequest> requests) {
        MembroUseCase.alocarMembros(id, requests);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/membros/{colaboradorId}")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<Void> desalocarMembro(@PathVariable UUID id, @PathVariable UUID colaboradorId) {
        MembroUseCase.encerrarVinculo(id, colaboradorId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/responsabilidade/{colaboradorId}")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<Void> transferirResponsabilidade(@PathVariable UUID id, @PathVariable UUID colaboradorId) {
        equipeUseCases.transferirResponsabilidade(id, colaboradorId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/membros")
    @PreAuthorize("hasAuthority('rh:equipe:read')")
    public ResponseEntity<List<EquipeMembroResponse>> listarMembrosAtivos(@PathVariable UUID id) {
        return ResponseEntity.ok(MembroUseCase.listarMembrosAtivos(id));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:equipe:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-equipes.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarEquipesUseCase.obterModeloCsv());
    }
}
