package com.nexo.app.modules.notificacao.doc;

import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.notificacao.application.dto.PoliticaObservabilidadeResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Contrato da rota privada consumida pelo adapter de observabilidade. */
@Tag(name = "Observabilidade - Política", description = "Projeção privada da audiência técnica.")
@SecurityRequirement(name = "technicalToken")
public interface PoliticaObservabilidadeControllerDoc {

    @Operation(summary = "Projetar política de alertas de observabilidade",
            description = "Retorna a política versionada e a audiência elegível para o adapter técnico.")
    ResponseEntity<PoliticaObservabilidadeResponse> projetar();
}
