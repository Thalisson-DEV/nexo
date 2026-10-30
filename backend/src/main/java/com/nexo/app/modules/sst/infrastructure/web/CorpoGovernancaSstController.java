package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.AtivacaoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstDetalheResponse;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstFilter;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstResponse;
import com.nexo.app.modules.sst.application.dto.CorpoGovernancaSstUpdateRequest;
import com.nexo.app.modules.sst.application.dto.EncerramentoCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.InclusaoMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.MembroCorpoSstResponse;
import com.nexo.app.modules.sst.application.dto.SaidaMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.dto.SubstituicaoMembroCorpoSstRequest;
import com.nexo.app.modules.sst.application.usecases.CorpoGovernancaSstUseCases;
import com.nexo.app.modules.sst.doc.CorpoGovernancaSstControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca/corpos")
@RequiredArgsConstructor
public class CorpoGovernancaSstController implements CorpoGovernancaSstControllerDoc {

    private final CorpoGovernancaSstUseCases useCases;

    @Override
    @PostMapping
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<CorpoGovernancaSstDetalheResponse> criar(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CorpoGovernancaSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<CorpoGovernancaSstDetalheResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarPorId(id));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<PageResponse<CorpoGovernancaSstResponse>> listar(
            @ParameterObject CorpoGovernancaSstFilter filter,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filter, pageable));
    }

    @Override
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<CorpoGovernancaSstDetalheResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody CorpoGovernancaSstUpdateRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PostMapping("/{idCorpo}/membros")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<MembroCorpoSstResponse> adicionarMembro(
            @PathVariable UUID idCorpo,
            @Valid @RequestBody InclusaoMembroCorpoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.adicionarMembro(idCorpo, request));
    }

    @Override
    @PostMapping("/{idCorpo}/membros/{idMembro}/saida")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<MembroCorpoSstResponse> registrarSaida(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID idCorpo,
            @PathVariable UUID idMembro,
            @Valid @RequestBody SaidaMembroCorpoSstRequest request) {
        return ResponseEntity.ok(useCases.registrarSaida(idCorpo, idMembro, request));
    }

    @Override
    @PostMapping("/{idCorpo}/membros/{idMembro}/substituicao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<MembroCorpoSstResponse> substituir(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID idCorpo,
            @PathVariable UUID idMembro,
            @Valid @RequestBody SubstituicaoMembroCorpoSstRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCases.substituir(idCorpo, idMembro, request));
    }

    @Override
    @PostMapping("/{id}/ativacao")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<CorpoGovernancaSstDetalheResponse> ativar(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody AtivacaoCorpoSstRequest request) {
        return ResponseEntity.ok(useCases.ativar(id, request));
    }

    @Override
    @PostMapping("/{id}/encerramento")
    @PreAuthorize("hasAuthority('sst:governanca:aprovar')")
    public ResponseEntity<CorpoGovernancaSstDetalheResponse> encerrar(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody EncerramentoCorpoSstRequest request) {
        return ResponseEntity.ok(useCases.encerrar(id, request));
    }
}
