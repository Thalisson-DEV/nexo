package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.*;
import com.nexo.app.modules.obras.application.usecases.AnexoObraUseCases;
import com.nexo.app.modules.obras.application.usecases.ExportarObrasUseCase;
import com.nexo.app.modules.obras.application.usecases.ImportarObrasUseCase;
import com.nexo.app.modules.obras.application.usecases.ObraUseCases;
import com.nexo.app.modules.obras.application.usecases.RelacionamentoProjetoUseCases;
import com.nexo.app.modules.obras.doc.ObraControllerDoc;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras")
@RequiredArgsConstructor
public class ObraController implements ObraControllerDoc {

    private final ObraUseCases useCases;
    private final AnexoObraUseCases anexoUseCases;
    private final ImportarObrasUseCase importarObrasUseCase;
    private final ExportarObrasUseCase exportarObrasUseCase;
    private final RelacionamentoProjetoUseCases relacionamentoProjetoUseCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<ObraResponse> criar(@Valid @RequestBody ObraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<ObraResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody ObraRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<ObraResponse> alterarStatus(
            @PathVariable UUID id,
            @RequestParam UUID idNovoStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate dataFimEfetiva) {
        return ResponseEntity.ok(useCases.alterarStatus(id, idNovoStatus, dataFimEfetiva));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<ObraResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<PageResponse<ObraResumoResponse>> listar(@ParameterObject ObraFilter filter,
                                                                   @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/{id}/projetos-relacionados")
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<List<ProjetoRelacionadoResponse>> listarProjetosRelacionados(@PathVariable UUID id) {
        return ResponseEntity.ok(relacionamentoProjetoUseCases.listar(id));
    }

    @Override
    @PostMapping("/{id}/projetos-relacionados")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<ProjetoRelacionadoResponse> vincularProjeto(
            @PathVariable UUID id,
            @Valid @RequestBody VinculoProjetoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(relacionamentoProjetoUseCases.vincular(id, request));
    }

    @Override
    @DeleteMapping("/{id}/projetos-relacionados/{idProjetoRelacionado}")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<Void> desvincularProjeto(
            @PathVariable UUID id,
            @PathVariable UUID idProjetoRelacionado) {
        relacionamentoProjetoUseCases.desvincular(id, idProjetoRelacionado);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{id}/subprojetos")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    @Deprecated(forRemoval = true)
    public ResponseEntity<ObraResponse> vincularSubprojeto(
            @PathVariable UUID id,
            @Valid @RequestBody SubprojetoVinculoRequest request) {
        return ResponseEntity.ok(useCases.vincularSubprojeto(id, request));
    }

    @Override
    @DeleteMapping("/{id}/subprojetos/{idFilho}")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    @Deprecated(forRemoval = true)
    public ResponseEntity<ObraResponse> desvincularSubprojeto(
            @PathVariable UUID id,
            @PathVariable UUID idFilho) {
        return ResponseEntity.ok(useCases.desvincularSubprojeto(id, idFilho));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('obras:anexos:write')")
    public ResponseEntity<AnexoObraResponse> anexar(
            @PathVariable UUID id,
            @RequestParam UUID idTipoAnexo,
            @RequestParam(required = false) String observacoes,
            @RequestPart MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoUseCases.anexar(id, idTipoAnexo, observacoes, arquivo));
    }

    @Override
    @GetMapping("/{id}/anexos")
    @PreAuthorize("hasAuthority('obras:anexos:read')")
    public ResponseEntity<List<AnexoObraResponse>> listarAnexos(@PathVariable UUID id) {
        return ResponseEntity.ok(anexoUseCases.listarPorObra(id));
    }

    @Override
    @GetMapping("/{id}/anexos/{idAnexo}/download")
    @PreAuthorize("hasAuthority('obras:anexos:read')")
    public ResponseEntity<StreamingResponseBody> baixarAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        DownloadAnexoObraDTO dto = anexoUseCases.baixar(id, idAnexo);
        StreamingResponseBody responseBody = outputStream -> {
            try (java.io.InputStream stream = dto.stream()) {
                stream.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.nomeArquivo() + "\"")
                .contentType(MediaType.parseMediaType(dto.contentType()))
                .contentLength(dto.tamanhoBytes())
                .body(responseBody);
    }

    @Override
    @DeleteMapping("/{id}/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('obras:anexos:write')")
    public ResponseEntity<Void> removerAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        anexoUseCases.remover(id, idAnexo);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping(value = "/importar/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<ImportacaoObraResponse> validarImportacao(@RequestPart MultipartFile arquivo) {
        return ResponseEntity.ok(importarObrasUseCase.executarValidacao(arquivo));
    }

    @Override
    @PostMapping("/importar/confirmar")
    @PreAuthorize("hasAuthority('obras:obra:import')")
    public ResponseEntity<ImportacaoObraResponse> confirmarImportacao(@Valid @RequestBody ConfirmarImportacaoObraRequest request) {
        return ResponseEntity.ok(importarObrasUseCase.confirmarImportacao(request));
    }

    @Override
    @GetMapping(value = "/importar/modelo", produces = "text/csv")
    @PreAuthorize("hasAnyAuthority('obras:obra:import', 'obras:obra:write')")
    public ResponseEntity<byte[]> baixarModeloCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modelo-importacao-obras.csv\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(importarObrasUseCase.obterModeloCsv());
    }

    @Override
    @GetMapping("/exportar/resumo")
    @PreAuthorize("hasAuthority('obras:obra:export')")
    public ResponseEntity<StreamingResponseBody> exportarResumo(@ParameterObject ObraFilter filter) {
        StreamingResponseBody responseBody = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                // UTF-8 BOM para Excel
                outputStream.write(0xEF);
                outputStream.write(0xBB);
                outputStream.write(0xBF);
                exportarObrasUseCase.exportarResumo(filter, writer);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"obras_resumo.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(responseBody);
    }

    @Override
    @GetMapping("/exportar/detalhado")
    @PreAuthorize("hasAuthority('obras:obra:export')")
    public ResponseEntity<StreamingResponseBody> exportarDetalhado(@ParameterObject ObraFilter filter) {
        StreamingResponseBody responseBody = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                // UTF-8 BOM para Excel
                outputStream.write(0xEF);
                outputStream.write(0xBB);
                outputStream.write(0xBF);
                exportarObrasUseCase.exportarDetalhado(filter, writer);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"obras_detalhado.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(responseBody);
    }
}
