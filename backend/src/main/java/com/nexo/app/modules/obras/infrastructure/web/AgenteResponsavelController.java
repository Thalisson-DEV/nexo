package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.AgenteResponsavelFilter;
import com.nexo.app.modules.obras.application.dto.AgenteResponsavelRequest;
import com.nexo.app.modules.obras.application.dto.AgenteResponsavelResponse;
import com.nexo.app.modules.obras.application.usecases.AgenteResponsavelUseCases;
import com.nexo.app.modules.obras.doc.AgenteResponsavelControllerDoc;
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
@RequestMapping("/v1/obras/agentes")
@RequiredArgsConstructor
public class AgenteResponsavelController implements AgenteResponsavelControllerDoc {

    private final AgenteResponsavelUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<AgenteResponsavelResponse> criar(@Valid @RequestBody AgenteResponsavelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:write')")
    public ResponseEntity<AgenteResponsavelResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody AgenteResponsavelRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<AgenteResponsavelResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<PageResponse<AgenteResponsavelResponse>> listar(@ParameterObject AgenteResponsavelFilter filter,
                                                                          @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/todos-ativos")
    @PreAuthorize("hasAuthority('obras:cadastros:read')")
    public ResponseEntity<List<AgenteResponsavelResponse>> listarTodosAtivos() {
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
