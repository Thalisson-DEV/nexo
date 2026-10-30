package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.application.dto.MetaFaturamentoFilter;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoRequest;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoResponse;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoUpdateRequest;
import com.nexo.app.modules.rh.application.usecases.MetaFaturamentoUseCases;
import com.nexo.app.modules.rh.doc.MetaFaturamentoControllerDoc;
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
@RequestMapping("/v1/metas-faturamento")
@RequiredArgsConstructor
public class MetaFaturamentoController implements MetaFaturamentoControllerDoc {

    private final MetaFaturamentoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('rh:meta-faturamento:write')")
    public ResponseEntity<List<MetaFaturamentoResponse>> criar(@Valid @RequestBody MetaFaturamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:meta-faturamento:read')")
    public ResponseEntity<MetaFaturamentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('rh:meta-faturamento:read')")
    public ResponseEntity<PageResponse<MetaFaturamentoResponse>> listar(
            @ParameterObject MetaFaturamentoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:meta-faturamento:write')")
    public ResponseEntity<MetaFaturamentoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody MetaFaturamentoUpdateRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:meta-faturamento:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:meta-faturamento:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
