package com.nexo.app.modules.notificacao.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nexo.app.modules.notificacao.application.dto.AnexoComunicadoDownload;
import com.nexo.app.modules.notificacao.application.dto.AnexoComunicadoResponse;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoFilter;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoResponse;
import com.nexo.app.modules.notificacao.application.dto.CorrigirTextoComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.PublicarComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.RelatorioConfirmacaoResponse;
import com.nexo.app.modules.notificacao.application.usecases.AnexoComunicadoUseCases;
import com.nexo.app.modules.notificacao.application.usecases.ComunicadoUseCases;
import com.nexo.app.modules.notificacao.doc.ComunicadoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/comunicados")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('notificacao:comunicado:manage')")
public class ComunicadoController implements ComunicadoControllerDoc {

    private final ComunicadoUseCases useCases;
    private final AnexoComunicadoUseCases anexoUseCases;

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<ComunicadoResponse>> listar(
            @ParameterObject ComunicadoFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<ComunicadoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscar(id));
    }

    @Override
    @PostMapping
    public ResponseEntity<ComunicadoResponse> criar(@Valid @RequestBody ComunicadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<ComunicadoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody ComunicadoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/texto")
    public ResponseEntity<ComunicadoResponse> corrigirTexto(
            @PathVariable UUID id, @Valid @RequestBody CorrigirTextoComunicadoRequest request) {
        return ResponseEntity.ok(
                useCases.corrigirTexto(id, request.titulo(), request.resumo(), request.conteudo()));
    }

    @Override
    @PostMapping("/{id}/publicacao")
    public ResponseEntity<ComunicadoResponse> publicar(
            @PathVariable UUID id, @RequestBody PublicarComunicadoRequest request) {
        return ResponseEntity.ok(useCases.publicar(id, request));
    }

    @Override
    @PatchMapping("/{id}/arquivamento")
    public ResponseEntity<ComunicadoResponse> arquivar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.arquivar(id));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        useCases.desativar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}/confirmacoes")
    public ResponseEntity<RelatorioConfirmacaoResponse> relatorioConfirmacao(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.relatorioConfirmacao(id));
    }

    @Override
    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnexoComunicadoResponse> anexar(
            @PathVariable UUID id, @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(anexoUseCases.anexar(id, arquivo));
    }

    @Override
    @GetMapping("/{id}/anexos/{idAnexo}")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        AnexoComunicadoDownload anexo = anexoUseCases.baixar(id, idAnexo);
        return respostaDeDownload(anexo);
    }

    @Override
    @DeleteMapping("/{id}/anexos/{idAnexo}")
    public ResponseEntity<Void> removerAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        anexoUseCases.remover(id, idAnexo);
        return ResponseEntity.noContent().build();
    }

    static ResponseEntity<Resource> respostaDeDownload(AnexoComunicadoDownload anexo) {
        String disposition = ContentDisposition.inline()
                .filename(anexo.nomeArquivo(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(anexo.contentType()))
                .contentLength(anexo.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new InputStreamResource(anexo.conteudo()));
    }
}
