package com.nexo.app.modules.telemetria.doc;

import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteRequest;
import com.nexo.app.modules.telemetria.application.dto.TelemetriaMobileDtos.LoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Telemetria mobile", description = "Diagnóstico técnico do app de campo, sem dados pessoais")
@SecurityRequirement(name = "bearerAuth")
public interface TelemetriaMobileControllerDoc {

    @Operation(summary = "Receber lote de eventos técnicos do app de campo",
            description = "Aceita de 1 a 100 eventos (tipo, instante, até 20 valores primitivos). "
                    + "Eventos com mais de 30 dias ou mais de 1 dia no futuro são descartados. "
                    + "Nenhum vínculo com o usuário autenticado é gravado. Retenção de 90 dias. "
                    + "Aceita Idempotency-Key para reenvio seguro do mesmo lote.")
    ResponseEntity<LoteResponse> receber(LoteRequest lote);
}
