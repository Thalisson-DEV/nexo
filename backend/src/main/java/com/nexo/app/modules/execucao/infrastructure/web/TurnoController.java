package com.nexo.app.modules.execucao.infrastructure.web;

import com.nexo.app.modules.execucao.TurnoApi;
import com.nexo.app.modules.execucao.application.dto.*;
import com.nexo.app.modules.execucao.doc.TurnoControllerDoc;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping({"/v1/turnos", "/api/v1/turnos"})
@RequiredArgsConstructor
public class TurnoController implements TurnoControllerDoc {

    private final TurnoApi api;

    @Override
    @PostMapping("/abrir")
    public ResponseEntity<TurnoResponse> abrir(@RequestBody @Valid AbrirTurnoRequest request) {
        return ResponseEntity.ok(api.abrir(request));
    }

    @Override
    @PostMapping("/{id}/fechar")
    public ResponseEntity<Void> fechar(@PathVariable UUID id) {
        api.fechar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/minha-equipe/composicao")
    public ResponseEntity<ComposicaoTurnoResponse> buscarComposicaoMinhaEquipe() {
        return ResponseEntity.ok(api.buscarComposicaoMinhaEquipe());
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<TurnoResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(api.buscarPorId(id));
    }

    @Override
    @GetMapping("/equipe/{idEquipe}/ativo")
    public ResponseEntity<TurnoResponse> buscarAtivoPorEquipe(@PathVariable UUID idEquipe) {
        return api.buscarAtivoPorEquipe(idEquipe)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Não há turno aberto para esta equipe."));
    }
}
