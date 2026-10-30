package com.nexo.app.modules.contrato.infrastructure.web;

import com.nexo.app.modules.contrato.AcaoResponse;
import com.nexo.app.modules.contrato.application.dto.*;
import com.nexo.app.modules.contrato.application.usecases.*;
import com.nexo.app.modules.contrato.doc.AcaoControllerDoc;
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
@RequestMapping("/v1/contratos/acoes")
@RequiredArgsConstructor
public class AcaoController implements AcaoControllerDoc {

    private final AcaoUseCases acaoUseCases;
    private final ImportarAcoesUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('contrato:acao:write')")
    public ResponseEntity<AcaoResponse> criar(@RequestBody @Valid AcaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(acaoUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('contrato:acao:write')")
    public ResponseEntity<ImportacaoAcaoResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('contrato:acao:write')")
    public ResponseEntity<ImportacaoAcaoResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoAcaoRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:acao:write')")
    public ResponseEntity<AcaoResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid AcaoRequest request) {
        return ResponseEntity.ok(acaoUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('contrato:acao:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        acaoUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:acao:read')")
    public ResponseEntity<AcaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(acaoUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('contrato:acao:read')")
    public ResponseEntity<PageResponse<AcaoResponse>> listar(AcaoFilter filter, Pageable pageable) {
        return ResponseEntity.ok(acaoUseCases.listar(filter, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('contrato:acao:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        acaoUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('contrato:acao:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-acoes.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
