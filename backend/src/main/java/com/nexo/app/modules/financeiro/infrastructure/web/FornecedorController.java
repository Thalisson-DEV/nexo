package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.dto.FornecedorFilter;
import com.nexo.app.modules.financeiro.application.dto.FornecedorRequest;
import com.nexo.app.modules.financeiro.application.dto.FornecedorResponse;
import com.nexo.app.modules.financeiro.application.dto.MotivoRequest;
import com.nexo.app.modules.financeiro.application.usecases.FornecedorUseCases;
import com.nexo.app.modules.financeiro.doc.FornecedorDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/financeiro/fornecedores")
@RequiredArgsConstructor
public class FornecedorController implements FornecedorDoc {

    private final FornecedorUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('financeiro:fornecedor:write')")
    public ResponseEntity<FornecedorResponse> criar(@Valid @RequestBody FornecedorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:fornecedor:write')")
    public ResponseEntity<FornecedorResponse> atualizar(@PathVariable UUID id,
                                                        @Valid @RequestBody FornecedorRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/homologar")
    @PreAuthorize("hasAuthority('financeiro:fornecedor:homologar')")
    public ResponseEntity<FornecedorResponse> homologar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.homologar(id));
    }

    @Override
    @PatchMapping("/{id}/bloquear")
    @PreAuthorize("hasAuthority('financeiro:fornecedor:homologar')")
    public ResponseEntity<FornecedorResponse> bloquear(@PathVariable UUID id,
                                                       @Valid @RequestBody MotivoRequest request) {
        return ResponseEntity.ok(useCases.bloquear(id, request.motivo()));
    }

    @Override
    @PatchMapping("/{id}/desbloquear")
    @PreAuthorize("hasAuthority('financeiro:fornecedor:homologar')")
    public ResponseEntity<FornecedorResponse> desbloquear(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.desbloquear(id));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:fornecedor:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('financeiro:fornecedor:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:fornecedor:read')")
    public ResponseEntity<FornecedorResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('financeiro:fornecedor:read')")
    public ResponseEntity<PageResponse<FornecedorResponse>> listar(@ParameterObject FornecedorFilter filtro,
                                                                    @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }
}
