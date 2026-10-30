package com.nexo.app.modules.iam.infrastructure.web;

import com.nexo.app.modules.iam.application.dto.*;
import com.nexo.app.modules.iam.application.usecases.*;
import com.nexo.app.modules.iam.doc.PerfilAcessoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/iam/perfis")
@RequiredArgsConstructor
public class PerfilAcessoController implements PerfilAcessoControllerDoc {

    private final PerfilAcessoUseCases useCases;
    private final ImportarPerfilAcessoUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<PerfilAcessoResponse> criar(@Valid @RequestBody PerfilAcessoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<PerfilAcessoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody PerfilAcessoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:perfil:read')")
    public ResponseEntity<PerfilAcessoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('iam:perfil:read')")
    public ResponseEntity<PageResponse<PerfilAcessoResponse>> listar(PerfilAcessoFilter filter, Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @PostMapping("/{id}/permissoes/{idPermissao}")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<Void> vincularPermissao(@PathVariable UUID id, @PathVariable UUID idPermissao) {
        useCases.vincularPermissao(id, idPermissao);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/permissoes/{idPermissao}")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<Void> removerPermissao(@PathVariable UUID id, @PathVariable UUID idPermissao) {
        useCases.removerPermissao(id, idPermissao);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{id}/permissoes")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<List<PermissaoResponse>> definirPermissoes(@PathVariable UUID id, @Valid @RequestBody PermissoesPerfilRequest request) {
        return ResponseEntity.ok(useCases.definirPermissoes(id, request));
    }

    @GetMapping("/{id}/permissoes")
    @PreAuthorize("hasAuthority('iam:perfil:read')")
    public ResponseEntity<List<PermissaoResponse>> listarPermissoesPorPerfil(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.listarPermissoesPorPerfil(id));
    }

    @PostMapping("/importar")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<ImportacaoPerfilAcessoResponse> importar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/importar/confirmar")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<ImportacaoPerfilAcessoResponse> confirmarImportacao(@RequestBody ConfirmarImportacaoPerfilAcessoRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('iam:perfil:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-perfis.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
