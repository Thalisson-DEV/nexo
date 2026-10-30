package com.nexo.app.modules.telemetria.infrastructure.web;

import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteRequest;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteResponse;
import com.nexo.app.modules.telemetria.application.usecases.TelemetriaMobileUseCases;
import com.nexo.app.modules.telemetria.doc.TelemetriaMobileControllerDoc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TelemetriaMobileController implements TelemetriaMobileControllerDoc {

    private final TelemetriaMobileUseCases useCases;

    @Override
    @PostMapping("/v1/telemetria/mobile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoteResponse> receber(@Valid @RequestBody LoteRequest lote) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(useCases.receber(lote));
    }
}
