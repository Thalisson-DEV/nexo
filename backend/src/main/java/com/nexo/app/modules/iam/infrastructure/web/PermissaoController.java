package com.nexo.app.modules.iam.infrastructure.web;

import com.nexo.app.modules.iam.application.dto.PermissaoFilter;
import com.nexo.app.modules.iam.application.dto.PermissaoRequest;
import com.nexo.app.modules.iam.application.dto.PermissaoResponse;
import com.nexo.app.modules.iam.application.usecases.PermissaoUseCases;
import com.nexo.app.modules.iam.doc.PermissaoControllerDoc;
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
@RequestMapping("/v1/iam/permissoes")
@RequiredArgsConstructor
public class PermissaoController implements PermissaoControllerDoc {

    private final PermissaoUseCases useCases;

    @PostMapping
    @PreAuthorize("hasAuthority('iam:permissao:write')")
    public ResponseEntity<PermissaoResponse> criar(@Valid @RequestBody PermissaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:permissao:write')")
    public ResponseEntity<PermissaoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody PermissaoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('iam:permissao:write')")
    public ResponseEntity<Void> alterarStatus(@PathVariable UUID id, @RequestParam boolean ativo) {
        useCases.alterarStatus(id, ativo);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:permissao:read')")
    public ResponseEntity<PermissaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('iam:permissao:read')")
    public ResponseEntity<PageResponse<PermissaoResponse>> listar(@ParameterObject PermissaoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }
}
