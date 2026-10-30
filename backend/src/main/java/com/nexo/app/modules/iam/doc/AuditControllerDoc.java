package com.nexo.app.modules.iam.doc;

import com.nexo.app.modules.iam.application.dto.HistoricoRegistroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@Tag(name = "Auditoria", description = "Visualização do histórico de alterações do sistema")
@SecurityRequirement(name = "bearerAuth")
public interface AuditControllerDoc {

    @Operation(summary = "Obter histórico de alterações de um registro")
    ResponseEntity<HistoricoRegistroResponse> obterHistorico(UUID registroId);
}
