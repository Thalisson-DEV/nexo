package com.nexo.app.modules.notificacao.infrastructure.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexo.app.modules.notificacao.application.dto.PoliticaObservabilidadeResponse;
import com.nexo.app.modules.notificacao.application.usecases.PoliticaObservabilidadeUseCases;
import com.nexo.app.modules.notificacao.doc.PoliticaObservabilidadeControllerDoc;

import lombok.RequiredArgsConstructor;

/**
 * Rota interna sem exposição pelo frontend. A autenticação técnica da plataforma
 * deve fornecer a autoridade exclusiva desta operação.
 */
@RestController
@RequestMapping("/internal/observability/notification-policy")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('OBSERVABILITY_READ')")
public class PoliticaObservabilidadeController implements PoliticaObservabilidadeControllerDoc {

    private final PoliticaObservabilidadeUseCases useCases;

    @Override
    @GetMapping
    public ResponseEntity<PoliticaObservabilidadeResponse> projetar() {
        return ResponseEntity.ok(useCases.projetar());
    }
}
