package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.TipoEquipeResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.*;
import com.nexo.app.modules.rh.doc.TipoEquipeControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/rh/tipos-equipe")
@RequiredArgsConstructor
public class TipoEquipeController implements TipoEquipeControllerDoc {

    private final TipoEquipeUseCases tipoEquipeUseCases;
    private final ImportarTipoEquipeUseCase importarTipoEquipeUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:tipo-equipe:write')")
    public ResponseEntity<TipoEquipeResponse> criar(@RequestBody @Valid TipoEquipeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipoEquipeUseCases.criar(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:tipo-equipe:write')")
    public ResponseEntity<TipoEquipeResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid TipoEquipeRequest request) {
        return ResponseEntity.ok(tipoEquipeUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:tipo-equipe:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        tipoEquipeUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:tipo-equipe:read')")
    public ResponseEntity<TipoEquipeResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(tipoEquipeUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:tipo-equipe:read')")
    public ResponseEntity<PageResponse<TipoEquipeResponse>> listar(@ParameterObject TipoEquipeFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(tipoEquipeUseCases.listar(filter, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:tipo-equipe:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        tipoEquipeUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/importar")
    @PreAuthorize("hasAuthority('rh:tipo-equipe:write')")
    public ResponseEntity<ImportacaoTipoEquipeResponse> importar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarTipoEquipeUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/importar/confirmar")
    @PreAuthorize("hasAuthority('rh:tipo-equipe:write')")
    public ResponseEntity<ImportacaoTipoEquipeResponse> confirmarImportacao(@RequestBody ConfirmarImportacaoTipoEquipeRequest request) {
        return ResponseEntity.ok(importarTipoEquipeUseCase.confirmarImportacao(request));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:tipo-equipe:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-tipos-equipe.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarTipoEquipeUseCase.obterModeloCsv());
    }
}
