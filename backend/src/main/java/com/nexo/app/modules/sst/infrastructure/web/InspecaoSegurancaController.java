package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AnexoDesvioResponse;
import com.nexo.app.modules.sst.application.dto.EvidenciaDownload;
import com.nexo.app.modules.sst.application.dto.EvidenciaInspecaoResponse;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaFilter;
import com.nexo.app.modules.sst.application.dto.InspecaoSegurancaResponse;
import com.nexo.app.modules.sst.application.dto.RegistrarInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.TratarDesvioRequest;
import com.nexo.app.modules.sst.application.usecases.AnexoDesvioUseCases;
import com.nexo.app.modules.sst.application.usecases.EvidenciaInspecaoUseCases;
import com.nexo.app.modules.sst.application.usecases.ExportarInspecoesUseCase;
import com.nexo.app.modules.sst.application.usecases.InspecaoSegurancaUseCases;
import com.nexo.app.modules.sst.doc.InspecaoSegurancaControllerDoc;
import com.nexo.app.modules.sst.domain.ContextoAnexo;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/inspecoes")
@RequiredArgsConstructor
public class InspecaoSegurancaController implements InspecaoSegurancaControllerDoc {

    private final InspecaoSegurancaUseCases useCases;
    private final AnexoDesvioUseCases anexoUseCases;
    private final EvidenciaInspecaoUseCases evidenciaUseCases;
    private final ExportarInspecoesUseCase exportarInspecoesUseCase;
    private final com.nexo.app.modules.sst.application.usecases.ExportarDesviosUseCase exportarDesviosUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<InspecaoSegurancaResponse> registrar(@Valid @RequestBody RegistrarInspecaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCases.registrar(request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PostMapping(value = "/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<EvidenciaInspecaoResponse> enviarEvidencia(@RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evidenciaUseCases.enviar(arquivo, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @DeleteMapping("/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<Void> descartarEvidenciaPendente(@PathVariable UUID idAnexo) {
        evidenciaUseCases.descartar(idAnexo, SecurityUtils.getUsuarioLogadoId());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping(value = "/{idInspecao}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<EvidenciaInspecaoResponse> anexarEvidenciaInspecao(
            @PathVariable UUID idInspecao, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(evidenciaUseCases.anexar(idInspecao, arquivo, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @GetMapping("/{idInspecao}/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<InputStreamResource> baixarEvidenciaInspecao(
            @PathVariable UUID idInspecao, @PathVariable UUID idAnexo) {
        EvidenciaDownload download = evidenciaUseCases.baixar(idInspecao, idAnexo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.conteudo()));
    }

    @Override
    @DeleteMapping("/{idInspecao}/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<Void> removerEvidenciaInspecao(@PathVariable UUID idInspecao, @PathVariable UUID idAnexo) {
        evidenciaUseCases.remover(idInspecao, idAnexo);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<InspecaoSegurancaResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<PageResponse<InspecaoSegurancaResponse>> listar(
            @ParameterObject InspecaoSegurancaFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @GetMapping("/minhas")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<PageResponse<InspecaoSegurancaResponse>> listarMinhas(
            @ParameterObject InspecaoSegurancaFilter filter, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listarPorInspetor(filter, pageable, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PutMapping("/{idInspecao}/desvios/{idDesvio}/tratamento")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<InspecaoSegurancaResponse> tratarDesvio(
            @PathVariable UUID idInspecao, @PathVariable UUID idDesvio, @Valid @RequestBody TratarDesvioRequest request) {
        return ResponseEntity.ok(useCases.tratarDesvio(idInspecao, idDesvio, request));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<InspecaoSegurancaResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody com.nexo.app.modules.sst.application.dto.AtualizarInspecaoRequest request) {
        return ResponseEntity.ok(useCases.atualizarInspecao(id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PutMapping("/{id}/cabecalho")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<InspecaoSegurancaResponse> atualizarCabecalho(
            @PathVariable UUID id, @Valid @RequestBody com.nexo.app.modules.sst.application.dto.AtualizarCabecalhoInspecaoRequest request) {
        return ResponseEntity.ok(useCases.atualizarCabecalho(id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @GetMapping("/{id}/historico")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<com.nexo.app.modules.sst.application.dto.HistoricoInspecaoResponse> obterHistorico(
            @PathVariable UUID id, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.obterHistorico(id, pageable));
    }

    @Override
    @PostMapping(value = "/{idInspecao}/desvios/{idDesvio}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<AnexoDesvioResponse> anexarEvidencia(
            @PathVariable UUID idInspecao, @PathVariable UUID idDesvio,
            @RequestParam ContextoAnexo contexto, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(anexoUseCases.anexar(idInspecao, idDesvio, contexto, arquivo));
    }

    @Override
    @GetMapping("/{idInspecao}/desvios/{idDesvio}/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<InputStreamResource> baixarEvidencia(
            @PathVariable UUID idInspecao, @PathVariable UUID idDesvio, @PathVariable UUID idAnexo) {
        EvidenciaDownload download = anexoUseCases.baixar(idInspecao, idDesvio, idAnexo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(download.conteudo()));
    }

    @Override
    @DeleteMapping("/{idInspecao}/desvios/{idDesvio}/anexos/{idAnexo}")
    @PreAuthorize("hasAuthority('sst:inspecao:write')")
    public ResponseEntity<Void> removerEvidencia(
            @PathVariable UUID idInspecao, @PathVariable UUID idDesvio, @PathVariable UUID idAnexo) {
        anexoUseCases.remover(idInspecao, idDesvio, idAnexo);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/exportar/resumo")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<StreamingResponseBody> exportarResumo(
            @ParameterObject InspecaoSegurancaFilter filter, @RequestParam(required = false, defaultValue = "false") Boolean apenasMinhas,
            @RequestParam(required = false, defaultValue = "csv") String formato) {
        return exportar(formato, "inspecoes_resumo", apenasMinhas,
                (idInspetor, writer) -> exportarInspecoesUseCase.exportarResumo(filter, idInspetor, writer),
                (idInspetor, saida) -> exportarInspecoesUseCase.exportarResumoXlsx(filter, idInspetor, saida));
    }

    @Override
    @GetMapping("/exportar/detalhado")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<StreamingResponseBody> exportarDetalhado(
            @ParameterObject InspecaoSegurancaFilter filter, @RequestParam(required = false, defaultValue = "false") Boolean apenasMinhas,
            @RequestParam(required = false, defaultValue = "csv") String formato) {
        return exportar(formato, "inspecoes_detalhado", apenasMinhas,
                (idInspetor, writer) -> exportarInspecoesUseCase.exportarDetalhado(filter, idInspetor, writer),
                (idInspetor, saida) -> exportarInspecoesUseCase.exportarDetalhadoXlsx(filter, idInspetor, saida));
    }

    @Override
    @GetMapping("/desvios/exportar/resumo")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<StreamingResponseBody> exportarDesviosResumo(
            @ParameterObject InspecaoSegurancaFilter filter, @RequestParam(required = false, defaultValue = "false") Boolean apenasMinhas,
            @RequestParam(required = false, defaultValue = "csv") String formato) {
        return exportar(formato, "desvios_resumo", apenasMinhas,
                (idInspetor, writer) -> exportarDesviosUseCase.exportarResumo(filter, idInspetor, writer),
                (idInspetor, saida) -> exportarDesviosUseCase.exportarResumoXlsx(filter, idInspetor, saida));
    }

    @Override
    @GetMapping("/desvios/exportar/detalhado")
    @PreAuthorize("hasAuthority('sst:inspecao:read')")
    public ResponseEntity<StreamingResponseBody> exportarDesviosDetalhado(
            @ParameterObject InspecaoSegurancaFilter filter, @RequestParam(required = false, defaultValue = "false") Boolean apenasMinhas,
            @RequestParam(required = false, defaultValue = "csv") String formato) {
        return exportar(formato, "desvios_detalhado", apenasMinhas,
                (idInspetor, writer) -> exportarDesviosUseCase.exportarDetalhado(filter, idInspetor, writer),
                (idInspetor, saida) -> exportarDesviosUseCase.exportarDetalhadoXlsx(filter, idInspetor, saida));
    }

    /** Adaptador HTTP comum das exportacoes: CSV (padrao, com BOM) ou XLSX (`formato=xlsx`). */
    private ResponseEntity<StreamingResponseBody> exportar(String formato, String nomeBase, Boolean apenasMinhas,
            ExportacaoCsv csv, ExportacaoXlsx xlsx) {
        UUID idInspetor = Boolean.TRUE.equals(apenasMinhas) ? SecurityUtils.getUsuarioLogadoId() : null;
        boolean planilha = "xlsx".equalsIgnoreCase(formato);
        StreamingResponseBody responseBody = planilha
                ? outputStream -> xlsx.exportar(idInspetor, outputStream)
                : outputStream -> {
                    try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                        writer.write('\uFEFF'); // BOM
                        csv.exportar(idInspetor, writer);
                    }
                };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nomeBase + (planilha ? ".xlsx" : ".csv") + "\"")
                .contentType(planilha
                        ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        : MediaType.parseMediaType("text/csv"))
                .body(responseBody);
    }

    @FunctionalInterface
    private interface ExportacaoCsv {
        void exportar(UUID idInspetor, Writer writer) throws java.io.IOException;
    }

    @FunctionalInterface
    private interface ExportacaoXlsx {
        void exportar(UUID idInspetor, java.io.OutputStream saida) throws java.io.IOException;
    }
}
