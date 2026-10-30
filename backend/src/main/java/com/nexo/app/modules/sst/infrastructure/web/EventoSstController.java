package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AnexoEventoResponse;
import com.nexo.app.modules.sst.application.dto.EventoSstFilter;
import com.nexo.app.modules.sst.application.dto.ConfirmarImportacaoEventoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstResponse;
import com.nexo.app.modules.sst.application.dto.EventoSstResumoResponse;
import com.nexo.app.modules.sst.application.dto.ImportacaoEventoSstResponse;
import com.nexo.app.modules.sst.application.dto.EvidenciaDownload;
import com.nexo.app.modules.sst.application.dto.MotivoEventoRequest;
import com.nexo.app.modules.sst.application.dto.TratativaEventoRequest;
import com.nexo.app.modules.sst.application.usecases.AnexoEventoSstUseCases;
import com.nexo.app.modules.sst.application.usecases.EventoSstUseCases;
import com.nexo.app.modules.sst.application.usecases.ExportarEventosSstUseCase;
import com.nexo.app.modules.sst.application.usecases.ImportarEventosSstUseCase;
import com.nexo.app.modules.sst.doc.EventoSstControllerDoc;
import com.nexo.app.modules.sst.domain.ContextoAnexoEvento;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/eventos")
@RequiredArgsConstructor
public class EventoSstController implements EventoSstControllerDoc {

    private final EventoSstUseCases useCases;
    private final AnexoEventoSstUseCases anexoUseCases;
    private final ExportarEventosSstUseCase exportarUseCase;
    private final ImportarEventosSstUseCase importarUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<EventoSstResponse> registrar(@Valid @RequestBody EventoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.registrar(request));
    }

    @Override
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<ImportacaoEventoSstResponse> importar(
            @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importarUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/confirmar-importacao")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<ImportacaoEventoSstResponse> confirmarImportacao(
            @Valid @RequestBody ConfirmarImportacaoEventoSstRequest request) {
        return ResponseEntity.ok(importarUseCase.confirmarImportacao(request));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<byte[]> baixarModeloImportacao() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"modelo-importacao-eventos-sst.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarUseCase.obterModeloCsv());
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<EventoSstResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody EventoSstRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:evento:read')")
    public ResponseEntity<EventoSstResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:evento:read')")
    public ResponseEntity<PageResponse<EventoSstResumoResponse>> listar(
            @ParameterObject EventoSstFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/exportar")
    @PreAuthorize("hasAuthority('sst:evento:read')")
    public ResponseEntity<StreamingResponseBody> exportar(@ParameterObject EventoSstFilter filter) {
        StreamingResponseBody corpo = saida -> {
            try (Writer writer = new OutputStreamWriter(saida, StandardCharsets.UTF_8)) {
                writer.write('﻿'); // BOM: sem ele o Excel abre os acentos errados
                exportarUseCase.exportar(filter, writer);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eventos_sst.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(corpo);
    }

    @Override
    @PutMapping("/{id}/tratativa")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<EventoSstResponse> iniciarTratativa(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TratativaEventoRequest request) {
        return ResponseEntity.ok(useCases.iniciarTratativa(id, request));
    }

    @Override
    @PutMapping("/{id}/conclusao")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<EventoSstResponse> concluir(
            @PathVariable UUID id, @Valid @RequestBody(required = false) TratativaEventoRequest request) {
        return ResponseEntity.ok(useCases.concluir(id, request));
    }

    @Override
    @PutMapping("/{id}/cancelamento")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<EventoSstResponse> cancelar(
            @PathVariable UUID id, @Valid @RequestBody MotivoEventoRequest request) {
        return ResponseEntity.ok(useCases.cancelar(id, request));
    }

    @Override
    @PutMapping("/{id}/reabertura")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<EventoSstResponse> reabrir(
            @PathVariable UUID id, @Valid @RequestBody MotivoEventoRequest request) {
        return ResponseEntity.ok(useCases.reabrir(id, request));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ //
    //  Evidências
    // ------------------------------------------------------------------ //

    @Override
    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<AnexoEventoResponse> anexar(
            @PathVariable UUID id,
            @RequestParam ContextoAnexoEvento contexto,
            @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoUseCases.anexar(id, contexto, arquivo));
    }

    @Override
    @GetMapping("/{id}/anexos")
    @PreAuthorize("hasAuthority('sst:evento:read')")
    public ResponseEntity<List<AnexoEventoResponse>> listarAnexos(@PathVariable UUID id) {
        return ResponseEntity.ok(anexoUseCases.listar(id));
    }

    @Override
    @GetMapping("/{id}/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('sst:evento:read')")
    public ResponseEntity<InputStreamResource> baixarAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        EvidenciaDownload download = anexoUseCases.baixar(id, idAnexo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.conteudo()));
    }

    @Override
    @DeleteMapping("/{id}/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('sst:evento:write')")
    public ResponseEntity<Void> removerAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        anexoUseCases.remover(id, idAnexo);
        return ResponseEntity.noContent().build();
    }
}
