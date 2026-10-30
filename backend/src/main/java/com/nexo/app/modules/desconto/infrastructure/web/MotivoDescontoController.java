package com.nexo.app.modules.desconto.infrastructure.web;

import com.nexo.app.modules.desconto.application.dto.MotivoDescontoRequest;
import com.nexo.app.modules.desconto.application.dto.MotivoDescontoResponse;
import com.nexo.app.modules.desconto.application.usecases.MotivoDescontoUseCases;
import com.nexo.app.modules.desconto.doc.MotivoDescontoDoc;
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
@RequestMapping("/v1/descontos/motivos")
@RequiredArgsConstructor
public class MotivoDescontoController implements MotivoDescontoDoc {

    private final MotivoDescontoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('desconto:motivo:write')")
    public ResponseEntity<MotivoDescontoResponse> criar(@Valid @RequestBody MotivoDescontoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:motivo:write')")
    public ResponseEntity<MotivoDescontoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody MotivoDescontoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:motivo:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('desconto:motivo:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:motivo:read')")
    public ResponseEntity<MotivoDescontoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('desconto:motivo:read')")
    public ResponseEntity<PageResponse<MotivoDescontoResponse>> listar(@ParameterObject com.nexo.app.modules.desconto.application.dto.MotivoDescontoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }
}
