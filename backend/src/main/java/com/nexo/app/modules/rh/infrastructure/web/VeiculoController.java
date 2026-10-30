package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.*;
import com.nexo.app.modules.rh.doc.VeiculoControllerDoc;
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
@RequestMapping("/v1/veiculos")
@RequiredArgsConstructor
public class VeiculoController implements VeiculoControllerDoc {

    private final VeiculoUseCases veiculoUseCases;
    private final ImportarVeiculosUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:veiculo:write')")
    public ResponseEntity<VeiculoResponse> criar(@Valid @RequestBody VeiculoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(veiculoUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:veiculo:write')")
    public ResponseEntity<ImportacaoVeiculoResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:veiculo:write')")
    public ResponseEntity<ImportacaoVeiculoResponse> confirmarImportacao(@Valid @RequestBody ConfirmarImportacaoVeiculoRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:veiculo:read')")
    public ResponseEntity<VeiculoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(veiculoUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:veiculo:read')")
    public ResponseEntity<PageResponse<VeiculoResponse>> listar(@ParameterObject VeiculoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(veiculoUseCases.listar(filter, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:veiculo:write')")
    public ResponseEntity<VeiculoResponse> atualizar(@PathVariable UUID id, @RequestBody VeiculoRequest request) {
        return ResponseEntity.ok(veiculoUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:veiculo:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        veiculoUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:veiculo:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        veiculoUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:veiculo:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-veiculos.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
