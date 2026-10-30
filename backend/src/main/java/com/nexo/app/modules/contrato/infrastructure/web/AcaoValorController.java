package com.nexo.app.modules.contrato.infrastructure.web;

import com.nexo.app.modules.contrato.application.dto.*;
import com.nexo.app.modules.contrato.application.usecases.*;
import com.nexo.app.modules.contrato.doc.AcaoValorControllerDoc;
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

@RestController
@RequestMapping("/v1/contratos/acoes-valores")
@RequiredArgsConstructor
public class AcaoValorController implements AcaoValorControllerDoc {

    private final AcaoValorUseCases acaoValorUseCases;
    private final ImportarAcaoValoresUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('contrato:acao-valor:write')")
    public ResponseEntity<AcaoValorResponse> criar(@RequestBody @Valid AcaoValorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(acaoValorUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('contrato:acao-valor:write')")
    public ResponseEntity<ImportacaoAcaoValorResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('contrato:acao-valor:write')")
    public ResponseEntity<ImportacaoAcaoValorResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoAcaoValorRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:acao-valor:write')")
    public ResponseEntity<AcaoValorResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid AcaoValorRequest request) {
        return ResponseEntity.ok(acaoValorUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('contrato:acao-valor:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        acaoValorUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:acao-valor:read')")
    public ResponseEntity<AcaoValorResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(acaoValorUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('contrato:acao-valor:read')")
    public ResponseEntity<PageResponse<AcaoValorResponse>> listar(AcaoValorFilter filter, Pageable pageable) {
        return ResponseEntity.ok(acaoValorUseCases.listar(filter, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:acao-valor:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        acaoValorUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('contrato:acao-valor:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-acoes-valores.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
