package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoPlanilhaRequest;
import com.nexo.app.modules.obras.application.dto.KitImportacaoLinha;
import com.nexo.app.modules.obras.application.dto.PreviaImportacaoObraResponse;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoFilter;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoRequest;
import com.nexo.app.modules.obras.application.dto.KitConstrutivoResponse;
import com.nexo.app.modules.obras.application.usecases.ImportarKitsUseCase;
import com.nexo.app.modules.obras.application.usecases.KitConstrutivoUseCases;
import com.nexo.app.modules.obras.doc.KitConstrutivoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras/kits")
@RequiredArgsConstructor
public class KitConstrutivoController implements KitConstrutivoControllerDoc {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final KitConstrutivoUseCases useCases;
    private final ImportarKitsUseCase importarKitsUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('obras:kit:write')")
    public ResponseEntity<KitConstrutivoResponse> criar(@Valid @RequestBody KitConstrutivoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:kit:write')")
    public ResponseEntity<KitConstrutivoResponse> atualizar(@PathVariable UUID id,
                                                            @Valid @RequestBody KitConstrutivoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:kit:read')")
    public ResponseEntity<KitConstrutivoResponse> buscarPorId(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia) {
        return ResponseEntity.ok(useCases.buscarPorId(id, dataReferencia));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('obras:kit:read')")
    public ResponseEntity<PageResponse<KitConstrutivoResponse>> listar(
            @ParameterObject KitConstrutivoFilter filter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, dataReferencia, pageable));
    }

    @Override
    @GetMapping("/grupos")
    @PreAuthorize("hasAuthority('obras:kit:read')")
    public ResponseEntity<List<String>> listarGrupos(@RequestParam(required = false) UUID idContrato) {
        return ResponseEntity.ok(useCases.listarGrupos(idContrato));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:kit:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping(value = "/importacoes/validacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('obras:kit:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<KitImportacaoLinha>> validarImportacao(
            @RequestParam UUID idContrato, @RequestPart MultipartFile arquivo) {
        return ResponseEntity.ok(importarKitsUseCase.validar(idContrato, arquivo));
    }

    @Override
    @PostMapping("/importacoes/revalidacao")
    @PreAuthorize("hasAuthority('obras:kit:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<KitImportacaoLinha>> revalidarImportacao(
            @Valid @RequestBody ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha> request) {
        return ResponseEntity.ok(importarKitsUseCase.revalidar(request));
    }

    @Override
    @PostMapping("/importacoes/confirmacao")
    @PreAuthorize("hasAuthority('obras:kit:import')")
    public ResponseEntity<PreviaImportacaoObraResponse<KitImportacaoLinha>> confirmarImportacao(
            @Valid @RequestBody ConfirmarImportacaoPlanilhaRequest<KitImportacaoLinha> request) {
        return ResponseEntity.ok(importarKitsUseCase.confirmar(request));
    }

    @Override
    @GetMapping(value = "/importacoes/modelo", produces = XLSX)
    @PreAuthorize("hasAuthority('obras:kit:import')")
    public ResponseEntity<byte[]> baixarModelo() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-kits.xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE, XLSX)
                .body(importarKitsUseCase.gerarModelo());
    }
}
