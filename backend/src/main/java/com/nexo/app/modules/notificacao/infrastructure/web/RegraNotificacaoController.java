package com.nexo.app.modules.notificacao.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.notificacao.application.dto.GatilhoResponse;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoFilter;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoRequest;
import com.nexo.app.modules.notificacao.application.dto.RegraNotificacaoResponse;
import com.nexo.app.modules.notificacao.application.usecases.RegraNotificacaoUseCases;
import com.nexo.app.modules.notificacao.doc.RegraNotificacaoControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/notificacoes/regras")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('notificacao:regra:manage')")
public class RegraNotificacaoController implements RegraNotificacaoControllerDoc {

    private final RegraNotificacaoUseCases useCases;

    @Override
    @GetMapping("/gatilhos")
    public ResponseEntity<List<GatilhoResponse>> listarGatilhos() {
        return ResponseEntity.ok(useCases.listarGatilhos());
    }

    @Override
    @GetMapping
    public ResponseEntity<PageResponse<RegraNotificacaoResponse>> listar(
            @ParameterObject RegraNotificacaoFilter filtro, @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(useCases.listar(filtro, pageable));
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<RegraNotificacaoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscar(id));
    }

    @Override
    @PostMapping
    public ResponseEntity<RegraNotificacaoResponse> criar(
            @Valid @RequestBody RegraNotificacaoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<RegraNotificacaoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody RegraNotificacaoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/ativacao")
    public ResponseEntity<RegraNotificacaoResponse> ativar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.ativar(id));
    }

    @Override
    @PatchMapping("/{id}/desativacao")
    public ResponseEntity<RegraNotificacaoResponse> desativar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.desativar(id));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        useCases.remover(id);
        return ResponseEntity.noContent().build();
    }
}
