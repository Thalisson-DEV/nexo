package com.nexo.app.modules.estoque.infrastructure.web;

import com.nexo.app.modules.estoque.application.dto.*;
import com.nexo.app.modules.estoque.application.usecases.EstoqueUseCases;
import com.nexo.app.modules.estoque.application.usecases.EstoqueLookupUseCase;
import com.nexo.app.modules.estoque.application.usecases.ImportarEstoqueUseCase;
import com.nexo.app.modules.estoque.doc.EstoqueControllerDoc;
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
import java.util.List;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequestMapping("/v1/estoques")
@RequiredArgsConstructor
public class EstoqueController implements EstoqueControllerDoc {

    private final EstoqueUseCases estoqueUseCases;
    private final EstoqueLookupUseCase estoqueLookupUseCase;
    private final ImportarEstoqueUseCase importarUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('estoque:estoque:write')")
    public ResponseEntity<EstoqueResponse> criar(@RequestBody @Valid EstoqueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estoqueUseCases.criar(request));
    }

    @Override
    @PostMapping(value = "/importar", consumes = MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('estoque:estoque:write')")
    public ResponseEntity<ImportacaoEstoqueResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('estoque:estoque:write')")
    public ResponseEntity<ImportacaoEstoqueResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoEstoqueRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:estoque:write')")
    public ResponseEntity<EstoqueResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody EstoqueRequest request) {
        return ResponseEntity.ok(estoqueUseCases.atualizar(id, request));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('estoque:estoque:read')")
    public ResponseEntity<PageResponse<EstoqueResponse>> listar(@ParameterObject EstoqueFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(estoqueUseCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('estoque:estoque:read')")
    public ResponseEntity<LookupPageDto<LookupOptionDto>> options(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) List<String> tipos) {
        return ResponseEntity.ok(estoqueLookupUseCase.execute(q, limit, cursor, tipos));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:estoque:read')")
    public ResponseEntity<EstoqueResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(estoqueUseCases.buscarPorId(id));
    }

    @Override
    @PatchMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('estoque:estoque:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        estoqueUseCases.ativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('estoque:estoque:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        estoqueUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('estoque:estoque:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-estoques.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
