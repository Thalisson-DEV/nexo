package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.StatusObraFilter;
import com.nexo.app.modules.obras.application.dto.StatusObraRequest;
import com.nexo.app.modules.obras.application.dto.StatusObraResponse;
import com.nexo.app.modules.obras.application.usecases.StatusObraUseCases;
import com.nexo.app.modules.obras.doc.StatusObraControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras/status")
@RequiredArgsConstructor
public class StatusObraController implements StatusObraControllerDoc {

    private final StatusObraUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<StatusObraResponse> criar(@Valid @RequestBody StatusObraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<StatusObraResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody StatusObraRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<StatusObraResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<PageResponse<StatusObraResponse>> listar(@ParameterObject StatusObraFilter filter,
                                                                   @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/todos-ativos")
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<List<StatusObraResponse>> listarTodosAtivos() {
        return ResponseEntity.ok(useCases.listarTodasAtivas());
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
