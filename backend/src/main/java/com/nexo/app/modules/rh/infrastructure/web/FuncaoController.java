package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.FuncaoResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.*;
import com.nexo.app.modules.rh.doc.FuncaoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/v1/rh/funcoes")
@RequiredArgsConstructor
public class FuncaoController implements FuncaoControllerDoc {

    private final FuncaoUseCases funcaoUseCases;
    private final ImportarFuncoesUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:funcao:write')")
    public ResponseEntity<FuncaoResponse> criar(@RequestBody @Valid FuncaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(funcaoUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:funcao:write')")
    public ResponseEntity<ImportacaoFuncaoResponse> validarImportacao(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:funcao:write')")
    public ResponseEntity<ImportacaoFuncaoResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoFuncaoRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:funcao:write')")
    public ResponseEntity<FuncaoResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid FuncaoRequest request) {
        return ResponseEntity.ok(funcaoUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:funcao:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        funcaoUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:funcao:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        funcaoUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:funcao:read')")
    public ResponseEntity<FuncaoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(funcaoUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:funcao:read')")
    public ResponseEntity<PageResponse<FuncaoResponse>> listar(@ParameterObject FuncaoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(funcaoUseCases.listar(filter, pageable));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:funcao:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-funcoes.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
