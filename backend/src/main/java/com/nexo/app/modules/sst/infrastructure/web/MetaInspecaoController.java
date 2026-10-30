package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetaFilter;
import com.nexo.app.modules.sst.application.dto.AcompanhamentoMetasResponse;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoFilter;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoResponse;
import com.nexo.app.modules.sst.application.usecases.AcompanhamentoMetasUseCases;
import com.nexo.app.modules.sst.application.usecases.MetaInspecaoUseCases;
import com.nexo.app.modules.sst.doc.MetaInspecaoControllerDoc;
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
@RequestMapping("/v1/sst/metas-inspecao")
@RequiredArgsConstructor
public class MetaInspecaoController implements MetaInspecaoControllerDoc {

    private final MetaInspecaoUseCases useCases;
    private final AcompanhamentoMetasUseCases acompanhamentoUseCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:meta:write')")
    public ResponseEntity<MetaInspecaoResponse> criar(@Valid @RequestBody MetaInspecaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:meta:read')")
    public ResponseEntity<MetaInspecaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:meta:read')")
    public ResponseEntity<PageResponse<MetaInspecaoResponse>> listar(
            @ParameterObject MetaInspecaoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:meta:write')")
    public ResponseEntity<MetaInspecaoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody MetaInspecaoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping("/acompanhamento")
    @PreAuthorize("hasAuthority('sst:meta:read')")
    public ResponseEntity<AcompanhamentoMetasResponse> acompanhar(@ParameterObject AcompanhamentoMetaFilter filter) {
        return ResponseEntity.ok(acompanhamentoUseCases.acompanhar(filter));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:meta:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('sst:meta:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
