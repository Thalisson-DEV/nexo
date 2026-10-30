package com.nexo.app.modules.iam.infrastructure.web;

import com.nexo.app.modules.iam.application.dto.*;
import com.nexo.app.modules.iam.application.usecases.*;
import com.nexo.app.modules.iam.doc.UsuarioControllerDoc;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/iam/usuarios")
@RequiredArgsConstructor
public class UsuarioController implements UsuarioControllerDoc {

    private final UsuarioUseCases useCases;
    private final FavoritoUseCases favoritoUseCases;
    private final ImportarUsuariosUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('iam:usuario:write')")
    public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('iam:usuario:write')")
    public ResponseEntity<ImportacaoUsuarioResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('iam:usuario:write')")
    public ResponseEntity<ImportacaoUsuarioResponse> confirmarImportacao(
            @Valid @RequestBody ConfirmarImportacaoUsuarioRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('iam:usuario:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"modelo-importacao-usuarios.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:usuario:write')")
    public ResponseEntity<UsuarioResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody UsuarioUpdateRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('iam:usuario:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:usuario:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('iam:usuario:read')")
    public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('iam:usuario:read')")
    public ResponseEntity<PageResponse<UsuarioResponse>> listar(@ParameterObject UsuarioFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(useCases.buscarPorId(usuario.getId()));
    }

    @Override
    @GetMapping("/me/favoritos")
    public ResponseEntity<List<FavoritoResponse>> listarFavoritos(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(favoritoUseCases.listarFavoritos(usuario.getId()));
    }

    @Override
    @PostMapping("/me/favoritos")
    public ResponseEntity<FavoritoResponse> adicionarFavorito(@AuthenticationPrincipal Usuario usuario, @Valid @RequestBody FavoritoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(favoritoUseCases.adicionarFavorito(usuario.getId(), request));
    }

    @Override
    @DeleteMapping("/me/favoritos/{id}")
    public ResponseEntity<Void> removerFavorito(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        favoritoUseCases.removerFavorito(usuario.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PatchMapping("/me/preferencias")
    public ResponseEntity<Void> atualizarPreferencias(@AuthenticationPrincipal Usuario usuario, @Valid @RequestBody PreferenciasRequest request) {
        useCases.atualizarPreferencias(usuario.getId(), request);
        return ResponseEntity.ok().build();
    }
}
