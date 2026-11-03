package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraFilter;
import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraRequest;
import com.nexo.app.modules.obras.application.dto.ModeloChecklistObraResponse;
import com.nexo.app.modules.obras.application.usecases.ModeloChecklistObraUseCases;
import com.nexo.app.modules.obras.doc.ModeloChecklistObraControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/obras/checklists")
@RequiredArgsConstructor
public class ModeloChecklistObraController implements ModeloChecklistObraControllerDoc {

    private static final String LER = "hasAuthority('obras:documentacao:read')";
    private static final String CONFIGURAR = "hasAuthority('obras:documentacao:manage')";

    private final ModeloChecklistObraUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize(CONFIGURAR)
    public ResponseEntity<ModeloChecklistObraResponse> criar(@Valid @RequestBody ModeloChecklistObraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize(CONFIGURAR)
    public ResponseEntity<ModeloChecklistObraResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody ModeloChecklistObraRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize(LER)
    public ResponseEntity<ModeloChecklistObraResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize(LER)
    public ResponseEntity<PageResponse<ModeloChecklistObraResponse>> listar(@ParameterObject ModeloChecklistObraFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @PostMapping("/{id}/duplicacao")
    @PreAuthorize(CONFIGURAR)
    public ResponseEntity<ModeloChecklistObraResponse> duplicar(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.duplicar(id));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize(CONFIGURAR)
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/ativar")
    @PreAuthorize(CONFIGURAR)
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
