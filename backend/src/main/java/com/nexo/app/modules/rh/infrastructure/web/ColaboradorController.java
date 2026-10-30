package com.nexo.app.modules.rh.infrastructure.web;

import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.application.dto.*;
import com.nexo.app.modules.rh.application.usecases.*;
import com.nexo.app.modules.rh.doc.ColaboradorControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.application.dto.LookupOptionDto;
import com.nexo.app.shared.application.dto.LookupPageDto;
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
@RequestMapping("/v1/rh/colaboradores")
@RequiredArgsConstructor
public class ColaboradorController implements ColaboradorControllerDoc {

    private final ColaboradorUseCases colaboradorUseCases;
    private final ImportarColaboradoresUseCase importarUseCase;
    private final SearchColaboradorOptionsUseCase searchOptionsUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<ColaboradorResponse> criar(@RequestBody @Valid ColaboradorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(colaboradorUseCases.criar(request));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<ImportacaoColaboradorResponse> importar(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<ImportacaoColaboradorResponse> confirmarImportacao(@RequestBody @Valid ConfirmarImportacaoRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @PostMapping("/gerar-acesso")
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<ColaboradorResponse> gerarAcesso(@RequestBody @Valid VincularAcessoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(colaboradorUseCases.gerarAcesso(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<ColaboradorResponse> atualizar(@PathVariable UUID id, @RequestBody @Valid ColaboradorUpdateRequest request) {
        return ResponseEntity.ok(colaboradorUseCases.atualizar(id, request));
    }

    @PutMapping("/{id}/ativar")
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<Void> ativar(@PathVariable UUID id) {
        colaboradorUseCases.ativar(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        colaboradorUseCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rh:colaborador:read')")
    public ResponseEntity<ColaboradorResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(colaboradorUseCases.buscarPorId(id));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('rh:colaborador:read')")
    public ResponseEntity<LookupPageDto<LookupOptionDto>> searchOptions(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(searchOptionsUseCase.execute(q, limit, cursor));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('rh:colaborador:read')")
    public ResponseEntity<PageResponse<ColaboradorResponse>> listar(@ParameterObject ColaboradorFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(colaboradorUseCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/coordenadores-supervisores")
    @PreAuthorize("hasAuthority('rh:colaborador:read')")
    public ResponseEntity<java.util.List<ColaboradorMinResponse>> listarCoordenadoresESupervisores() {
        return ResponseEntity.ok(colaboradorUseCases.listarCoordenadoresESupervisores());
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('rh:colaborador:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-colaboradores.csv\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }
}
