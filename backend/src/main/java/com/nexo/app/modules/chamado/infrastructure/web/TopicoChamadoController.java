package com.nexo.app.modules.chamado.infrastructure.web;

import com.nexo.app.modules.chamado.application.dto.TopicoChamadoFilter;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoResponse;
import com.nexo.app.modules.chamado.application.usecases.TopicoChamadoUseCases;
import com.nexo.app.modules.chamado.doc.TopicoChamadoControllerDoc;
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
@RequestMapping("/v1/chamados/topicos")
@RequiredArgsConstructor
public class TopicoChamadoController implements TopicoChamadoControllerDoc {

    private final TopicoChamadoUseCases useCases;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('chamado:read')")
    public ResponseEntity<List<TopicoChamadoResponse>> listarDisponiveis() {
        return ResponseEntity.ok(useCases.listarDisponiveis());
    }

    @Override
    @GetMapping("/administracao")
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<PageResponse<TopicoChamadoResponse>> listarAdministracao(
            @ParameterObject TopicoChamadoFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listarAdministracao(filtro, pageable));
    }

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<TopicoChamadoResponse> criar(@Valid @RequestBody TopicoChamadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<TopicoChamadoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody TopicoChamadoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/ativacao")
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/{id}/desativacao")
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
