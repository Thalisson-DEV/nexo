package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.CatalogoSstFilter;
import com.nexo.app.modules.sst.application.dto.CatalogoSstRequest;
import com.nexo.app.modules.sst.application.dto.CatalogoSstResponse;
import com.nexo.app.modules.sst.application.usecases.CatalogoSstUseCases;
import com.nexo.app.modules.sst.doc.CatalogoSstControllerDoc;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
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
@RequestMapping("/v1/sst/catalogos")
@RequiredArgsConstructor
public class CatalogoSstController implements CatalogoSstControllerDoc {

    private final CatalogoSstUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:catalogo:write')")
    public ResponseEntity<CatalogoSstResponse> criar(@Valid @RequestBody CatalogoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:catalogo:read')")
    public ResponseEntity<CatalogoSstResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:catalogo:read')")
    public ResponseEntity<PageResponse<CatalogoSstResponse>> listar(
            @ParameterObject CatalogoSstFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/opcoes")
    @PreAuthorize("hasAuthority('sst:catalogo:read')")
    public ResponseEntity<List<CatalogoSstResponse>> listarOpcoes(@RequestParam TipoCatalogoSst tipo) {
        return ResponseEntity.ok(useCases.listarOpcoes(tipo));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:catalogo:write')")
    public ResponseEntity<CatalogoSstResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody CatalogoSstRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:catalogo:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('sst:catalogo:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }
}
