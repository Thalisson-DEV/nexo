package com.nexo.app.modules.notificacao.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.notificacao.application.dto.AdiarComunicadoRequest;
import com.nexo.app.modules.notificacao.application.dto.ComunicadoMuralResponse;
import com.nexo.app.modules.notificacao.application.usecases.AnexoComunicadoUseCases;
import com.nexo.app.modules.notificacao.application.usecases.MuralUseCases;
import com.nexo.app.modules.notificacao.doc.MuralControllerDoc;

import lombok.RequiredArgsConstructor;

/**
 * Consumo do mural. Não exige permissão além de estar autenticado: um comunicado
 * que não alcança parte do público-alvo por falta de permissão derrota o próprio
 * propósito de ter sido publicado — quem vê o quê já é decidido pelos segmentos.
 */
@RestController
@RequestMapping("/v1/mural")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MuralController implements MuralControllerDoc {

    private final MuralUseCases useCases;
    private final AnexoComunicadoUseCases anexoUseCases;

    @Override
    @GetMapping
    public ResponseEntity<List<ComunicadoMuralResponse>> mural() {
        return ResponseEntity.ok(useCases.mural());
    }

    @Override
    @PatchMapping("/{id}/visualizacao")
    public ResponseEntity<Void> registrarVisualizacao(@PathVariable UUID id) {
        useCases.registrarVisualizacao(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dispensar(@PathVariable UUID id) {
        useCases.dispensar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{id}/adiamento")
    public ResponseEntity<Void> adiar(@PathVariable UUID id,
                                      @RequestBody(required = false) AdiarComunicadoRequest request) {
        useCases.adiar(id, request == null ? null : request.adiarAte());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/{id}/confirmacao")
    public ResponseEntity<Void> confirmar(@PathVariable UUID id) {
        useCases.confirmar(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping("/{id}/anexos/{idAnexo}")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable UUID id, @PathVariable UUID idAnexo) {
        return ComunicadoController.respostaDeDownload(anexoUseCases.baixar(id, idAnexo));
    }
}
