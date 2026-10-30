package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.MotivoPendenciaRequest;
import com.nexo.app.modules.estoque.application.dto.MotivoPendenciaResponse;
import com.nexo.app.modules.estoque.application.usecases.MotivoPendenciaUseCases;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/motivos-pendencia")
@RequiredArgsConstructor
public class MotivoPendenciaController {
    private final MotivoPendenciaUseCases useCases;

    @GetMapping
    @PreAuthorize("hasAuthority('estoque:motivo-rejeicao:read')")
    public List<MotivoPendenciaResponse> listar(@RequestParam(defaultValue = "false") boolean apenasAtivos) {
        return apenasAtivos ? useCases.listarAtivos() : useCases.listar();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('estoque:motivo-rejeicao:write')")
    public ResponseEntity<MotivoPendenciaResponse> criar(@Valid @RequestBody MotivoPendenciaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:motivo-rejeicao:write')")
    public MotivoPendenciaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody MotivoPendenciaRequest request) {
        return useCases.atualizar(id, request);
    }

    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('estoque:motivo-rejeicao:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:motivo-rejeicao:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
