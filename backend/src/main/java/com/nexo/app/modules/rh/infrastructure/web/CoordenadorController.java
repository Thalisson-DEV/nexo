package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.CoordenadorResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.CoordenadorUseCases;
import com.nexo.app.modules.rh.application.usecases.ImportarCoordenadoresUseCase;
import com.nexo.app.modules.rh.doc.CoordenadorControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/rh/coordenadores")
@RequiredArgsConstructor
public class CoordenadorController implements CoordenadorControllerDoc {

    private final CoordenadorUseCases coordenadorUseCases;
    private final ImportarCoordenadoresUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:coordenador:write')")
    public ResponseEntity<CoordenadorResponse> criar(@RequestBody @Valid CoordenadorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coordenadorUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:coordenador:write')")
    public ResponseEntity<ImportacaoCoordenadorResponse> validarImportacao(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:coordenador:write')")
    public ResponseEntity<ImportacaoCoordenadorResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoCoordenadorRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:coordenador:write')")
    public ResponseEntity<CoordenadorResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid CoordenadorRequest request) {
        return ResponseEntity.ok(coordenadorUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:coordenador:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        coordenadorUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:coordenador:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        coordenadorUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:coordenador:read')")
    public ResponseEntity<CoordenadorResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(coordenadorUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:coordenador:read')")
    public ResponseEntity<PageResponse<CoordenadorResponse>> listar(@ParameterObject CoordenadorFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(coordenadorUseCases.listar(filter, pageable));
    }

    @GetMapping("/min")
    @PreAuthorize("hasAuthority('rh:coordenador:read')")
    public ResponseEntity<List<CoordenadorMinResponse>> listarAtivosMin() {
        return ResponseEntity.ok(coordenadorUseCases.listarAtivosMin());
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:coordenador:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-coordenadores.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
