package com.nexo.app.modules.contrato.infrastructure.web;

import com.nexo.app.modules.contrato.application.dto.*;
import com.nexo.app.modules.contrato.application.usecases.*;
import com.nexo.app.modules.contrato.doc.ContratoControllerDoc;
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

import java.util.UUID;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequestMapping("/v1/contratos")
@RequiredArgsConstructor
public class ContratoController implements ContratoControllerDoc {

    private final ContratoUseCases contratoUseCases;
    private final ImportarContratosUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('contrato:contrato:write')")
    public ResponseEntity<ContratoResponse> criar(@RequestBody @Valid ContratoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contratoUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('contrato:contrato:write')")
    public ResponseEntity<ImportacaoContratoResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('contrato:contrato:write')")
    public ResponseEntity<ImportacaoContratoResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoContratoRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:contrato:write')")
    public ResponseEntity<ContratoResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid ContratoRequest request) {
        return ResponseEntity.ok(contratoUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('contrato:contrato:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        contratoUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:contrato:read')")
    public ResponseEntity<ContratoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(contratoUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('contrato:contrato:read')")
    public ResponseEntity<PageResponse<ContratoResponse>> listar(ContratoFilter filter, Pageable pageable) {
        return ResponseEntity.ok(contratoUseCases.listar(filter, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:contrato:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        contratoUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('contrato:contrato:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-contratos.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
