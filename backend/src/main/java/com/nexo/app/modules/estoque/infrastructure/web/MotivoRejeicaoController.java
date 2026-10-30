package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoFilter;
import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoRequest;
import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoResponse;
import com.nexo.app.modules.estoque.application.usecases.MotivoRejeicaoUseCases;
import com.nexo.app.modules.estoque.doc.MotivoRejeicaoControllerDoc;
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
@RequestMapping("/v1/motivos-rejeicao")
@RequiredArgsConstructor
public class MotivoRejeicaoController implements MotivoRejeicaoControllerDoc {

    private final MotivoRejeicaoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAnyAuthority('estoque:motivo-rejeicao:write')")
    public ResponseEntity<MotivoRejeicaoResponse> criar(@RequestBody @Valid MotivoRejeicaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('estoque:motivo-rejeicao:write')")
    public ResponseEntity<MotivoRejeicaoResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid MotivoRejeicaoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAnyAuthority('estoque:motivo-rejeicao:read')")
    public ResponseEntity<PageResponse<MotivoRejeicaoResponse>> listar(@ParameterObject MotivoRejeicaoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('estoque:motivo-rejeicao:read')")
    public ResponseEntity<MotivoRejeicaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAnyAuthority('estoque:motivo-rejeicao:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('estoque:motivo-rejeicao:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
