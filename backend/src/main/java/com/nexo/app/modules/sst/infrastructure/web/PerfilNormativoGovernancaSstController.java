package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.ConfirmacaoPerfilNormativoSstRequest;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.PerfilNormativoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.usecases.PerfilNormativoGovernancaSstUseCases;
import com.nexo.app.modules.sst.doc.PerfilNormativoGovernancaSstControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca/perfis-normativos")
@RequiredArgsConstructor
public class PerfilNormativoGovernancaSstController
        implements PerfilNormativoGovernancaSstControllerDoc {

    private final PerfilNormativoGovernancaSstUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<PerfilNormativoGovernancaSstResponse> criar(
            @Valid @RequestBody PerfilNormativoGovernancaSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PostMapping("/{id}/confirmar")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<PerfilNormativoGovernancaSstResponse> confirmar(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmacaoPerfilNormativoSstRequest request) {
        return ResponseEntity.ok(useCases.confirmar(id, request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PerfilNormativoGovernancaSstResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<PerfilNormativoGovernancaSstResponse>> listar(
            @ParameterObject PerfilNormativoGovernancaSstFilter filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }
}
