package com.nexo.app.modules.chamado.infrastructure.web;

import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoFilter;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoRequest;
import com.nexo.app.modules.chamado.application.dto.EquipeAtendimentoResponse;
import com.nexo.app.modules.chamado.application.usecases.EquipeAtendimentoUseCases;
import com.nexo.app.modules.chamado.doc.EquipeAtendimentoControllerDoc;
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
@RequestMapping("/v1/chamados/equipes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('chamado:manage')")
public class EquipeAtendimentoController implements EquipeAtendimentoControllerDoc {

    private final EquipeAtendimentoUseCases useCases;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<EquipeAtendimentoResponse>> listar(
            @ParameterObject EquipeAtendimentoFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<EquipeAtendimentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PostMapping
    public ResponseEntity<EquipeAtendimentoResponse> criar(
            @Valid @RequestBody EquipeAtendimentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<EquipeAtendimentoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody EquipeAtendimentoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/ativacao")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/desativacao")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
