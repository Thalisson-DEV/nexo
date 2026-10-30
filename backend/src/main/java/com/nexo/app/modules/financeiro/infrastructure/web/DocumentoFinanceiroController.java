package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroFilter;
import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoRequest;
import com.nexo.app.modules.financeiro.application.dto.MotivoRequest;
import com.nexo.app.modules.financeiro.application.usecases.DocumentoFinanceiroUseCases;
import com.nexo.app.modules.financeiro.doc.DocumentoFinanceiroDoc;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
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

import java.util.UUID;

@RestController
@RequestMapping("/v1/financeiro/documentos")
@RequiredArgsConstructor
public class DocumentoFinanceiroController implements DocumentoFinanceiroDoc {

    private final DocumentoFinanceiroUseCases useCases;

    @Override
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('financeiro:documento:write')")
    public ResponseEntity<DocumentoFinanceiroResponse> enviar(
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestPart(value = "dados", required = false) @Valid DocumentoRequest dados,
            @AuthenticationPrincipal Usuario usuario) {
        // Quem enviou vem do token, nunca do payload.
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.enviar(arquivo, dados, usuario.getId()));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:documento:write')")
    public ResponseEntity<DocumentoFinanceiroResponse> atualizar(@PathVariable UUID id,
                                                                  @Valid @RequestBody DocumentoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/vincular")
    @PreAuthorize("hasAuthority('financeiro:documento:write')")
    public ResponseEntity<DocumentoFinanceiroResponse> vincular(@PathVariable UUID id,
                                                                 @RequestParam TipoSolicitacao tipo,
                                                                 @RequestParam UUID solicitacaoId) {
        return ResponseEntity.ok(useCases.vincular(id, tipo, solicitacaoId));
    }

    @Override
    @PatchMapping("/{id}/desvincular")
    @PreAuthorize("hasAuthority('financeiro:documento:write')")
    public ResponseEntity<DocumentoFinanceiroResponse> desvincular(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.desvincular(id));
    }

    @Override
    @PatchMapping("/{id}/pendencia")
    @PreAuthorize("hasAuthority('financeiro:documento:conferir')")
    public ResponseEntity<DocumentoFinanceiroResponse> apontarPendencia(@PathVariable UUID id,
                                                                        @Valid @RequestBody MotivoRequest request,
                                                                        @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(useCases.apontarPendencia(id, request.motivo(), usuario.getId()));
    }

    @Override
    @PatchMapping("/{id}/arquivar")
    @PreAuthorize("hasAuthority('financeiro:documento:conferir')")
    public ResponseEntity<DocumentoFinanceiroResponse> arquivar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.arquivar(id));
    }

    @Override
    @PatchMapping("/{id}/reabrir")
    @PreAuthorize("hasAuthority('financeiro:documento:conferir')")
    public ResponseEntity<DocumentoFinanceiroResponse> reabrir(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.reabrir(id));
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:documento:write')")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        useCases.remover(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}/conteudo")
    @PreAuthorize("hasAuthority('financeiro:documento:read')")
    public ResponseEntity<Resource> baixar(@PathVariable UUID id) {
        var documento = useCases.baixar(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.contentType()))
                .contentLength(documento.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + documento.nomeArquivo() + "\"")
                .body(new InputStreamResource(documento.conteudo()));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('financeiro:documento:read')")
    public ResponseEntity<DocumentoFinanceiroResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('financeiro:documento:read')")
    public ResponseEntity<PageResponse<DocumentoFinanceiroResponse>> listar(
            @ParameterObject DocumentoFinanceiroFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }
}
