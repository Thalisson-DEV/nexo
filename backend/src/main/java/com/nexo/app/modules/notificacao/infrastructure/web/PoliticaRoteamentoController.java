package com.nexo.app.modules.notificacao.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.notificacao.application.dto.PoliticaRoteamentoRequest;
import com.nexo.app.modules.notificacao.application.dto.PoliticaRoteamentoResponse;
import com.nexo.app.modules.notificacao.application.usecases.PoliticaRoteamentoUseCases;
import com.nexo.app.modules.notificacao.doc.PoliticaRoteamentoControllerDoc;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/notificacoes/politicas-roteamento")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('notificacao:regra:manage')")
public class PoliticaRoteamentoController implements PoliticaRoteamentoControllerDoc {

    private final PoliticaRoteamentoUseCases useCases;

    @Override
    @GetMapping
    public ResponseEntity<List<PoliticaRoteamentoResponse>> listar() {
        return ResponseEntity.ok(useCases.listar());
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<PoliticaRoteamentoResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscar(id));
    }

    @Override
    @PostMapping
    public ResponseEntity<PoliticaRoteamentoResponse> criar(
            @Valid @RequestBody PoliticaRoteamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCases.criar(request));
    }

    @Override
    @PutMapping("/{id}")
    public ResponseEntity<PoliticaRoteamentoResponse> atualizar(
            @PathVariable UUID id, @Valid @RequestBody PoliticaRoteamentoRequest request) {
        return ResponseEntity.ok(useCases.atualizar(id, request));
    }

    @Override
    @PatchMapping("/{id}/ativacao")
    public ResponseEntity<PoliticaRoteamentoResponse> ativar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.ativar(id));
    }

    @Override
    @PatchMapping("/{id}/desativacao")
    public ResponseEntity<PoliticaRoteamentoResponse> desativar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.desativar(id));
    }
}
