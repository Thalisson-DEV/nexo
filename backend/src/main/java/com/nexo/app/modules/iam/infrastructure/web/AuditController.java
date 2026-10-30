package com.nexo.app.modules.iam.infrastructure.web;

import com.nexo.app.modules.iam.application.dto.HistoricoRegistroResponse;
import com.nexo.app.modules.iam.application.usecases.BuscarHistoricoRegistroUseCase;
import com.nexo.app.modules.iam.doc.AuditControllerDoc;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/auditoria")
@RequiredArgsConstructor
public class AuditController implements AuditControllerDoc {

    private final BuscarHistoricoRegistroUseCase buscarHistoricoUseCase;

    @Override
    @GetMapping("/{registroId}")
    public ResponseEntity<HistoricoRegistroResponse> obterHistorico(@PathVariable UUID registroId) {
        return ResponseEntity.ok(buscarHistoricoUseCase.executar(registroId));
    }
}
