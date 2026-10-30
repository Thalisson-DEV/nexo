package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.application.dto.CentroFaturamentoFilter;
import com.nexo.app.modules.rh.application.dto.CentroFaturamentoRequest;
import com.nexo.app.modules.rh.CentroFaturamentoResponse;
import com.nexo.app.modules.rh.application.usecases.*;
import com.nexo.app.modules.rh.doc.CentroFaturamentoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/centros-faturamento")
@RequiredArgsConstructor
public class CentroFaturamentoController implements CentroFaturamentoControllerDoc {

    private final CentroFaturamentoUseCases CentroFaturamentoUseCases;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:centro-faturamento:write')")
    public ResponseEntity<CentroFaturamentoResponse> criar(@RequestBody CentroFaturamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CentroFaturamentoUseCases.criar(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:centro-faturamento:read')")
    public ResponseEntity<CentroFaturamentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(CentroFaturamentoUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:centro-faturamento:read')")
    public ResponseEntity<PageResponse<CentroFaturamentoResponse>> listar(@ParameterObject CentroFaturamentoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(CentroFaturamentoUseCases.listar(filter, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:centro-faturamento:write')")
    public ResponseEntity<CentroFaturamentoResponse> atualizar(@PathVariable UUID id, @RequestBody CentroFaturamentoRequest request) {
        return ResponseEntity.ok(CentroFaturamentoUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:centro-faturamento:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        CentroFaturamentoUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:centro-faturamento:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        CentroFaturamentoUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
