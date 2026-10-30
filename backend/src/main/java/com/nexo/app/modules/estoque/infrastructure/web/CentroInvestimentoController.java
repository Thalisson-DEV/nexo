package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.CentroInvestimentoResponse;
import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.application.usecases.CentroInvestimentoUseCases;
import com.nexo.app.modules.estoque.application.usecases.ImportarCentroInvestimentoUseCase;
import com.nexo.app.modules.estoque.doc.CentroInvestimentoControllerDoc;
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
@RequestMapping("/v1/centros-investimento")
@RequiredArgsConstructor
public class CentroInvestimentoController implements CentroInvestimentoControllerDoc {

    private final CentroInvestimentoUseCases useCases;
    private final ImportarCentroInvestimentoUseCase importarUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('estoque:centro-investimento:write')")
    public ResponseEntity<CentroInvestimentoResponse> criar(@RequestBody @Valid CentroInvestimentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PostMapping(value = "/importar", consumes = MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('estoque:centro-investimento:write')")
    public ResponseEntity<ImportacaoCentroInvestimentoResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('estoque:centro-investimento:write')")
    public ResponseEntity<ImportacaoCentroInvestimentoResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoCentroInvestimentoRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:centro-investimento:write')")
    public ResponseEntity<CentroInvestimentoResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody CentroInvestimentoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:centro-investimento:read')")
    public ResponseEntity<PageResponse<CentroInvestimentoResponse>> listar(@ParameterObject CentroInvestimentoFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:centro-investimento:read')")
    public ResponseEntity<CentroInvestimentoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('estoque:centro-investimento:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        useCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:centro-investimento:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('estoque:centro-investimento:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-centros-investimento.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
