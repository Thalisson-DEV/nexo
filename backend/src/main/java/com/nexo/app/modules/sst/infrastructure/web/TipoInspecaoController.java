package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.TipoInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoResponse;
import com.nexo.app.modules.sst.application.usecases.TipoInspecaoUseCases;
import com.nexo.app.modules.sst.doc.TipoInspecaoControllerDoc;
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
@RequestMapping("/v1/sst/tipos-inspecao")
@RequiredArgsConstructor
public class TipoInspecaoController implements TipoInspecaoControllerDoc {

    private final TipoInspecaoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:inspecao:write') or hasAuthority('sst:catalogo:write')")
    public ResponseEntity<TipoInspecaoResponse> criar(@Valid @RequestBody TipoInspecaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:inspecao:read') or hasAuthority('sst:catalogo:read')")
    public ResponseEntity<TipoInspecaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:inspecao:read') or hasAuthority('sst:catalogo:read')")
    public ResponseEntity<PageResponse<TipoInspecaoResponse>> listar(
            @ParameterObject TipoInspecaoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/opcoes")
    @PreAuthorize("hasAuthority('sst:inspecao:read') or hasAuthority('sst:catalogo:read')")
    public ResponseEntity<List<TipoInspecaoResponse>> listarOpcoes() {
        return ResponseEntity.ok(useCases.listarAtivos());
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:inspecao:write') or hasAuthority('sst:catalogo:write')")
    public ResponseEntity<TipoInspecaoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody TipoInspecaoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('sst:inspecao:write') or hasAuthority('sst:catalogo:write')")
    public ResponseEntity<Void> alternarStatus(@PathVariable UUID id) {
        useCases.alternarStatus(id);
        return ResponseEntity.noContent().build();
    }
}
