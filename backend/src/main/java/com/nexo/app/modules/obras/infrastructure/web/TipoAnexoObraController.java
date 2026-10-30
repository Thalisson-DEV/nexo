package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.TipoAnexoObraFilter;
import com.nexo.app.modules.obras.application.dto.TipoAnexoObraRequest;
import com.nexo.app.modules.obras.application.dto.TipoAnexoObraResponse;
import com.nexo.app.modules.obras.application.usecases.TipoAnexoObraUseCases;
import com.nexo.app.modules.obras.doc.TipoAnexoObraControllerDoc;
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
@RequestMapping("/v1/obras/tipos-anexos")
@RequiredArgsConstructor
public class TipoAnexoObraController implements TipoAnexoObraControllerDoc {

    private final TipoAnexoObraUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<TipoAnexoObraResponse> criar(@Valid @RequestBody TipoAnexoObraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<TipoAnexoObraResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody TipoAnexoObraRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<TipoAnexoObraResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<PageResponse<TipoAnexoObraResponse>> listar(@ParameterObject TipoAnexoObraFilter filter,
                                                                      @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/todos-ativos")
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<List<TipoAnexoObraResponse>> listarTodosAtivos() {
        return ResponseEntity.ok(useCases.listarTodosAtivos());
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
