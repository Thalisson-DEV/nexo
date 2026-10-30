package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.application.usecases.ImportarTipoEstoqueUseCase;
import com.nexo.app.modules.estoque.application.usecases.TipoEstoqueUseCases;
import com.nexo.app.modules.estoque.doc.TipoEstoqueControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequestMapping("/v1/tipos-estoque")
@RequiredArgsConstructor
public class TipoEstoqueController implements TipoEstoqueControllerDoc {

    private final TipoEstoqueUseCases useCases;
    private final ImportarTipoEstoqueUseCase importarUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:write')")
    public ResponseEntity<TipoEstoqueResponse> criar(@RequestBody @Valid TipoEstoqueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PostMapping(value = "/importar", consumes = MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:write')")
    public ResponseEntity<ImportacaoTipoEstoqueResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:write')")
    public ResponseEntity<ImportacaoTipoEstoqueResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoTipoEstoqueRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:write')")
    public ResponseEntity<TipoEstoqueResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody TipoEstoqueRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:read')")
    public ResponseEntity<PageResponse<TipoEstoqueResponse>> listar(@ParameterObject TipoEstoqueFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:read')")
    public ResponseEntity<TipoEstoqueResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('estoque:tipo-estoque:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-tipos-estoque.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
