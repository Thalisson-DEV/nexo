package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.dto.AprovacaoRequest;
import com.nexo.app.modules.financeiro.application.dto.EventoSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.MotivoRequest;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraFilter;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.financeiro.application.usecases.ExportarSolicitacoesFinanceirasUseCase;
import com.nexo.app.modules.financeiro.application.usecases.SolicitacaoCompraUseCases;
import com.nexo.app.modules.financeiro.doc.SolicitacaoCompraDoc;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.domain.TipoDocumentoFinanceiro;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/v1/financeiro/solicitacoes-compra")
@RequiredArgsConstructor
public class SolicitacaoCompraController implements SolicitacaoCompraDoc {

    private final SolicitacaoCompraUseCases useCases;
    private final ExportarSolicitacoesFinanceirasUseCase exportacaoUseCase;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('financeiro:compra:write')")
    public ResponseEntity<SolicitacaoCompraResponse> criar(@Valid @RequestBody SolicitacaoRequest request,
                                                           @AuthenticationPrincipal Usuario usuario) {
        // O solicitante vem do token, nunca do payload.
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request, usuario.getId()));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:compra:write')")
    public ResponseEntity<SolicitacaoCompraResponse> atualizar(@PathVariable UUID id,
                                                               @Valid @RequestBody SolicitacaoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/submeter")
    @PreAuthorize("hasAuthority('financeiro:compra:write')")
    public ResponseEntity<SolicitacaoCompraResponse> submeter(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.submeter(id));
    }

    @Override
    @PatchMapping("/{id}/aprovar")
    @PreAuthorize("hasAuthority('financeiro:compra:aprovar')")
    public ResponseEntity<SolicitacaoCompraResponse> aprovar(@PathVariable UUID id,
                                                              @Valid @RequestBody AprovacaoRequest request,
                                                              @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(useCases.aprovar(id, request, usuario.getId()));
    }

    @Override
    @PatchMapping("/{id}/rejeitar")
    @PreAuthorize("hasAuthority('financeiro:compra:aprovar')")
    public ResponseEntity<SolicitacaoCompraResponse> rejeitar(@PathVariable UUID id,
                                                               @Valid @RequestBody MotivoRequest request,
                                                               @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(useCases.rejeitar(id, request.motivo(), usuario.getId()));
    }

    @Override
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('financeiro:compra:write')")
    public ResponseEntity<SolicitacaoCompraResponse> cancelar(@PathVariable UUID id,
                                                               @Valid @RequestBody MotivoRequest request,
                                                               @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(useCases.cancelar(id, request.motivo(), usuario.getId()));
    }

    @Override
    @PatchMapping("/{id}/desfazer-decisao")
    @PreAuthorize("hasAuthority('financeiro:compra:aprovar')")
    public ResponseEntity<SolicitacaoCompraResponse> desfazerDecisao(@PathVariable UUID id,
                                                                      @Valid @RequestBody MotivoRequest request,
                                                                      @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(useCases.desfazerDecisao(id, request.motivo(), usuario.getId()));
    }

    @Override
    @GetMapping("/{id}/historico")
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<List<EventoSolicitacaoResponse>> historico(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.historico(id));
    }

    @Override
    @PostMapping(value = "/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('financeiro:documento:write')")
    public ResponseEntity<SolicitacaoCompraResponse> adicionarDocumento(
            @PathVariable UUID id,
            @RequestPart("arquivo") List<MultipartFile> arquivos,
            @RequestParam(required = false) TipoDocumentoFinanceiro tipo,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCases.adicionarDocumento(id, arquivos, tipo, usuario.getId()));
    }

    @Override
    @DeleteMapping("/{id}/documentos/{idDocumento}")
    @PreAuthorize("hasAuthority('financeiro:documento:write')")
    public ResponseEntity<Void> removerDocumento(@PathVariable UUID id, @PathVariable UUID idDocumento) {
        useCases.removerDocumento(id, idDocumento);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}/documentos/{idDocumento}")
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<Resource> baixarDocumento(@PathVariable UUID id, @PathVariable UUID idDocumento) {
        var documento = useCases.baixarDocumento(id, idDocumento);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.contentType()))
                .contentLength(documento.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + documento.nomeArquivo() + "\"")
                .body(new InputStreamResource(documento.conteudo()));
    }

    @Override
    @GetMapping("/{id}/documentos")
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<List<DocumentoResumoResponse>> listarDocumentos(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.listarDocumentos(id));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<SolicitacaoCompraResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping("/numero/{numero}")
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<SolicitacaoCompraResponse> buscarPorNumero(@PathVariable Long numero) {
        return ResponseEntity.ok(useCases.buscarPorNumero(numero));
    }

    @Override
    @GetMapping("/exportar/resumo")
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<StreamingResponseBody> exportarResumo(@ParameterObject SolicitacaoCompraFilter filtro) {
        return csv("solicitacoes_compra_resumo.csv",
                writer -> exportacaoUseCase.exportarComprasResumo(filtro, writer));
    }

    @Override
    @GetMapping("/exportar/detalhado")
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<StreamingResponseBody> exportarDetalhado(@ParameterObject SolicitacaoCompraFilter filtro) {
        return csv("solicitacoes_compra_detalhado.csv",
                writer -> exportacaoUseCase.exportarComprasDetalhado(filtro, writer));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('financeiro:compra:read')")
    public ResponseEntity<PageResponse<SolicitacaoCompraResponse>> listar(
            @ParameterObject SolicitacaoCompraFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    private ResponseEntity<StreamingResponseBody> csv(String nomeArquivo, EscritorCsv escritor) {
        StreamingResponseBody body = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write('\uFEFF');
                escritor.escrever(writer);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @FunctionalInterface
    private interface EscritorCsv {
        void escrever(Writer writer) throws java.io.IOException;
    }
}
