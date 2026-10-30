package com.nexo.app.modules.execucao.infrastructure.web;

import com.nexo.app.modules.execucao.application.dto.TipoServicoFilter;
import com.nexo.app.modules.execucao.application.dto.TipoServicoRequest;
import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import com.nexo.app.modules.execucao.application.usecases.TipoServicoUseCases;
import com.nexo.app.modules.execucao.doc.TipoServicoControllerDoc;
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
@RequestMapping("/v1/tipos-servico")
@RequiredArgsConstructor
public class TipoServicoController implements TipoServicoControllerDoc {

    private final TipoServicoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('execucao:tipo-servico:write')")
    public ResponseEntity<TipoServicoResponse> criar(@Valid @RequestBody TipoServicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('execucao:tipo-servico:read')")
    public ResponseEntity<TipoServicoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('execucao:tipo-servico:read')")
    public ResponseEntity<PageResponse<TipoServicoResponse>> listar(
            @ParameterObject TipoServicoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    // Quem lança ou consulta execuções precisa das opções, mesmo sem acesso ao cadastro.
    @Override
    @GetMapping("/opcoes")
    @PreAuthorize("hasAuthority('execucao:tipo-servico:read') or hasAuthority('execucao:comercial:read') "
            + "or hasAuthority('execucao:comercial:write')")
    public ResponseEntity<List<TipoServicoResponse>> listarOpcoes() {
        return ResponseEntity.ok(useCases.listarOpcoes());
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('execucao:tipo-servico:write')")
    public ResponseEntity<TipoServicoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody TipoServicoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('execucao:tipo-servico:write')")
    public ResponseEntity<Void> alternarStatus(@PathVariable UUID id) {
        useCases.alternarStatus(id);
        return ResponseEntity.noContent().build();
    }
}
