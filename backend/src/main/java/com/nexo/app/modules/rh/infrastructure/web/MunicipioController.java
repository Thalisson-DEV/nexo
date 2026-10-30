package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.MunicipioResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.ImportarMunicipiosUseCase;
import com.nexo.app.modules.rh.application.usecases.MunicipioUseCases;
import com.nexo.app.modules.rh.doc.MunicipioControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@RestController
@RequestMapping("/v1/municipios")
@RequiredArgsConstructor
public class MunicipioController implements MunicipioControllerDoc {

    private final MunicipioUseCases municipioUseCases;
    private final ImportarMunicipiosUseCase importarUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:municipio:write')")
    public ResponseEntity<MunicipioResponse> criar(@RequestBody @Valid MunicipioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(municipioUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('rh:municipio:write')")
    public ResponseEntity<ImportacaoMunicipioResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:municipio:write')")
    public ResponseEntity<ImportacaoMunicipioResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoMunicipioRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:municipio:write')")
    public ResponseEntity<MunicipioResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid MunicipioRequest request) {
        return ResponseEntity.ok(municipioUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:municipio:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        municipioUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:municipio:read')")
    public ResponseEntity<MunicipioResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(municipioUseCases.buscarPorId(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rh:municipio:read')")
    public ResponseEntity<PageResponse<MunicipioResponse>> listar(MunicipioFilter filter, Pageable pageable) {
        return ResponseEntity.ok(municipioUseCases.listar(filter, pageable));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:municipio:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        municipioUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:municipio:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-municipios.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
