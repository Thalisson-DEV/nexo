package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.*;
import com.nexo.app.modules.sst.application.usecases.ExigenciaTreinamentoSstUseCases;
import com.nexo.app.modules.sst.application.usecases.MatrizTreinamentoSstUseCases;
import com.nexo.app.modules.sst.application.usecases.TreinamentoGovernancaSstUseCases;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca")
@RequiredArgsConstructor
public class TreinamentoGovernancaSstController {
    private final TreinamentoGovernancaSstUseCases treinamentos;
    private final ExigenciaTreinamentoSstUseCases exigencias;
    private final MatrizTreinamentoSstUseCases matriz;

    @PostMapping("/treinamentos")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<TreinamentoGovernancaSstResponse> criar(
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody TreinamentoGovernancaSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(treinamentos.criar(request));
    }
    @PutMapping("/treinamentos/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<TreinamentoGovernancaSstResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody TreinamentoGovernancaSstRequest request) {
        return ResponseEntity.ok(treinamentos.atualizar(id, request));
    }
    @GetMapping("/treinamentos/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<TreinamentoGovernancaSstResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(treinamentos.buscarPorId(id));
    }
    @GetMapping("/treinamentos")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<TreinamentoGovernancaSstResponse>> listar(
            @ParameterObject TreinamentoGovernancaSstFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(treinamentos.listar(filtro, pageable));
    }
    @DeleteMapping("/treinamentos/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        treinamentos.desativar(id); return ResponseEntity.noContent().build();
    }

    @PostMapping("/exigencias-treinamento")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ExigenciaTreinamentoSstResponse> criarExigencia(
            @RequestHeader("Idempotency-Key") String chave,
            @Valid @RequestBody ExigenciaTreinamentoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                exigencias.criar(request, SecurityUtils.getUsuarioLogadoId()));
    }
    @PostMapping("/exigencias-treinamento/{id}/nova-versao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<ExigenciaTreinamentoSstResponse> versionarExigencia(
            @RequestHeader("Idempotency-Key") String chave, @PathVariable UUID id,
            @Valid @RequestBody ExigenciaTreinamentoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                exigencias.versionar(id, request, SecurityUtils.getUsuarioLogadoId()));
    }
    @PostMapping("/exigencias-treinamento/{id}/confirmacao-fonte")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<ExigenciaTreinamentoSstResponse> confirmarFonte(@PathVariable UUID id) {
        return ResponseEntity.ok(exigencias.confirmarFonte(id, SecurityUtils.getUsuarioLogadoId()));
    }
    @GetMapping("/exigencias-treinamento/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<ExigenciaTreinamentoSstResponse> buscarExigencia(@PathVariable UUID id) {
        return ResponseEntity.ok(exigencias.buscarPorId(id));
    }
    @GetMapping("/exigencias-treinamento")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<ExigenciaTreinamentoSstResponse>> listarExigencias(
            @ParameterObject ExigenciaTreinamentoSstFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(exigencias.listar(filtro, pageable));
    }

    @GetMapping("/matriz")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<MatrizTreinamentoSstResponse>> matriz(
            @ParameterObject MatrizTreinamentoSstFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(matriz.listar(filtro, pageable));
    }
    @GetMapping("/matriz/resumo")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<ResumoMatrizTreinamentoSstResponse> resumo(
            @ParameterObject MatrizTreinamentoSstFilter filtro) {
        return ResponseEntity.ok(matriz.resumo(filtro));
    }
}
