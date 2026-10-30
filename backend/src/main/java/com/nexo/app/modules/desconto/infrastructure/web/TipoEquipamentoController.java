package com.nexo.app.modules.desconto.infrastructure.web;

import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoRequest;
import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoResponse;
import com.nexo.app.modules.desconto.application.usecases.TipoEquipamentoUseCases;
import com.nexo.app.modules.desconto.doc.TipoEquipamentoDoc;
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
@RequestMapping("/v1/descontos/tipos-equipamento")
@RequiredArgsConstructor
public class TipoEquipamentoController implements TipoEquipamentoDoc {

    private final TipoEquipamentoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('desconto:tipo-equipamento:write')")
    public ResponseEntity<TipoEquipamentoResponse> criar(@Valid @RequestBody TipoEquipamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:tipo-equipamento:write')")
    public ResponseEntity<TipoEquipamentoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody TipoEquipamentoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:tipo-equipamento:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('desconto:tipo-equipamento:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('desconto:tipo-equipamento:read')")
    public ResponseEntity<TipoEquipamentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('desconto:tipo-equipamento:read')")
    public ResponseEntity<PageResponse<TipoEquipamentoResponse>> listar(@ParameterObject com.nexo.app.modules.desconto.application.dto.TipoEquipamentoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }
}
