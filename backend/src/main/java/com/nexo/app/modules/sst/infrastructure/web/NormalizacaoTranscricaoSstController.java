package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.NormalizacaoTranscricaoDto;
import com.nexo.app.modules.sst.application.usecases.NormalizacaoTranscricaoSstUseCases;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/sst/governanca/reunioes/{idReuniao}/normalizacoes")
@RequiredArgsConstructor
public class NormalizacaoTranscricaoSstController {
    private final NormalizacaoTranscricaoSstUseCases useCases;

    @PostMapping
    @PreAuthorize("hasAuthority('sst:ata:generate')")
    public ResponseEntity<NormalizacaoTranscricaoDto.Response> solicitar(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID idReuniao,
            @Valid @RequestBody NormalizacaoTranscricaoDto.SolicitarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(useCases.solicitar(idReuniao, request, SecurityUtils.getUsuarioLogadoId()));
    }

    @PostMapping("/{idNormalizacao}/confirmacao")
    @PreAuthorize("hasAuthority('sst:governanca:write')")
    public ResponseEntity<NormalizacaoTranscricaoDto.Response> confirmar(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID idReuniao,
            @PathVariable UUID idNormalizacao) {
        return ResponseEntity.ok(useCases.confirmar(
                idReuniao, idNormalizacao, SecurityUtils.getUsuarioLogadoId()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sst:governanca:read')")
    public ResponseEntity<List<NormalizacaoTranscricaoDto.Response>> listar(@PathVariable UUID idReuniao) {
        return ResponseEntity.ok(useCases.listar(idReuniao));
    }
}
