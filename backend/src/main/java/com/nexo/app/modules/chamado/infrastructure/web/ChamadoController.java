package com.nexo.app.modules.chamado.infrastructure.web;

import com.nexo.app.modules.chamado.application.dto.AbrirChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.AnexoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.AtualizarAtendimentoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.ChamadoFilter;
import com.nexo.app.modules.chamado.application.dto.ChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.InteracaoChamadoResponse;
import com.nexo.app.modules.chamado.application.dto.ReabrirChamadoRequest;
import com.nexo.app.modules.chamado.application.usecases.ChamadoUseCases;
import com.nexo.app.modules.chamado.doc.ChamadoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/v1/chamados")
@RequiredArgsConstructor
public class ChamadoController implements ChamadoControllerDoc {

    private final ChamadoUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('chamado:write')")
    public ResponseEntity<ChamadoResponse> abrir(@Valid @RequestBody AbrirChamadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCases.abrir(request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @GetMapping("/meus")
    @PreAuthorize("hasAuthority('chamado:read')")
    public ResponseEntity<PageResponse<ChamadoResponse>> listarMeus(
            @ParameterObject ChamadoFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listarMeus(
                SecurityUtils.getUsuarioLogadoId(), filtro, pageable));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<PageResponse<ChamadoResponse>> listarTodos(
            @ParameterObject ChamadoFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listarTodos(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('chamado:read', 'chamado:manage')")
    public ResponseEntity<ChamadoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PatchMapping("/meus/{id}/cancelamento")
    @PreAuthorize("hasAuthority('chamado:write')")
    public ResponseEntity<ChamadoResponse> cancelarMeuChamado(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.cancelarMeuChamado(id, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PostMapping("/meus/{id}/interacoes")
    @PreAuthorize("hasAuthority('chamado:write')")
    public ResponseEntity<InteracaoChamadoResponse> responderMeuChamado(
            @PathVariable UUID id, @Valid @RequestBody InteracaoChamadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.responderMeuChamado(
                id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PatchMapping("/meus/{id}/reabertura")
    @PreAuthorize("hasAuthority('chamado:write')")
    public ResponseEntity<ChamadoResponse> reabrirMeuChamado(
            @PathVariable UUID id, @Valid @RequestBody ReabrirChamadoRequest request) {
        return ResponseEntity.ok(useCases.reabrirMeuChamado(
                id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PostMapping("/{id}/interacoes")
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<InteracaoChamadoResponse> adicionarInteracaoAtendimento(
            @PathVariable UUID id, @Valid @RequestBody InteracaoChamadoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.adicionarInteracaoAtendimento(
                id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @GetMapping("/{id}/interacoes")
    @PreAuthorize("hasAnyAuthority('chamado:read', 'chamado:manage')")
    public ResponseEntity<java.util.List<InteracaoChamadoResponse>> listarInteracoes(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.listarInteracoes(id, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PatchMapping("/{id}/atendimento")
    @PreAuthorize("hasAuthority('chamado:manage')")
    public ResponseEntity<ChamadoResponse> atualizarAtendimento(
            @PathVariable UUID id, @Valid @RequestBody AtualizarAtendimentoChamadoRequest request) {
        return ResponseEntity.ok(useCases.atualizarAtendimento(
                id, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @Override
    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('chamado:write', 'chamado:manage')")
    public ResponseEntity<AnexoChamadoResponse> anexar(
            @PathVariable UUID id, @RequestPart("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCases.anexar(id, SecurityUtils.getUsuarioLogadoId(), arquivo));
    }

    @Override
    @GetMapping("/{id}/anexos/{idAnexo}/download")
    @PreAuthorize("hasAnyAuthority('chamado:read', 'chamado:manage')")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        var anexo = useCases.baixarAnexo(id, idAnexo, SecurityUtils.getUsuarioLogadoId());
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
