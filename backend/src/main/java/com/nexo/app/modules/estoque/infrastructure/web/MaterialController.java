package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.MaterialResponse;
import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.application.usecases.ImportarMaterialUseCase;
import com.nexo.app.modules.estoque.application.usecases.MaterialLookupUseCase;
import com.nexo.app.modules.estoque.application.usecases.MaterialUseCases;
import com.nexo.app.modules.estoque.doc.MaterialControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
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
@RequestMapping("/v1/materiais")
@RequiredArgsConstructor
public class MaterialController implements MaterialControllerDoc {

    private final MaterialUseCases materialUseCases;
    private final MaterialLookupUseCase materialLookupUseCase;
    private final ImportarMaterialUseCase importarUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAnyAuthority('estoque:material:write')")
    public ResponseEntity<MaterialResponse> criar(@RequestBody @Valid MaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialUseCases.criar(request));
    }

    @Override
    @PostMapping(value = "/importar", consumes = MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('estoque:material:write')")
    public ResponseEntity<ImportacaoMaterialResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAnyAuthority('estoque:material:write')")
    public ResponseEntity<ImportacaoMaterialResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoMaterialRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('estoque:material:write')")
    public ResponseEntity<MaterialResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody MaterialRequest request) {
        return ResponseEntity.ok(materialUseCases.atualizar(id, request));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAnyAuthority('estoque:material:read')")
    public ResponseEntity<PageResponse<MaterialResponse>> listar(@ParameterObject MaterialFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(materialUseCases.listar(filter, pageable));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('estoque:material:read')")
    public ResponseEntity<LookupPageDto<LookupOptionDto>> options(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(materialLookupUseCase.execute(q, limit, cursor));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('estoque:material:read')")
    public ResponseEntity<MaterialResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(materialUseCases.buscarPorId(id));
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAnyAuthority('estoque:material:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        materialUseCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('estoque:material:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        materialUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAnyAuthority('estoque:material:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-materiais.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
