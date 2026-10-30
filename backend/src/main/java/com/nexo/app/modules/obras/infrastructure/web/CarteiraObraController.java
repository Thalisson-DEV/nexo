package com.nexo.app.modules.obras.infrastructure.web;

import com.nexo.app.modules.obras.application.dto.CarteiraObraRequest;
import com.nexo.app.modules.obras.application.dto.CarteiraObraResponse;
import com.nexo.app.modules.obras.application.dto.HistoricoStatusObraResponse;
import com.nexo.app.modules.obras.application.dto.IndicadoresCarteiraResponse;
import com.nexo.app.modules.obras.application.dto.ObraFilter;
import com.nexo.app.modules.obras.application.dto.ObraProximaResponse;
import com.nexo.app.modules.obras.application.dto.OrcamentoObraResponse;
import com.nexo.app.modules.obras.application.dto.StatusOperacionalObraRequest;
import com.nexo.app.modules.obras.application.usecases.CarteiraObraUseCases;
import com.nexo.app.modules.obras.doc.CarteiraObraControllerDoc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/obras")
@RequiredArgsConstructor
public class CarteiraObraController implements CarteiraObraControllerDoc {

    private final CarteiraObraUseCases useCases;

    @Override
    @GetMapping("/{id}/carteira")
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<CarteiraObraResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarCarteira(id));
    }

    @Override
    @PutMapping("/{id}/carteira")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<CarteiraObraResponse> informar(@PathVariable UUID id,
                                                         @Valid @RequestBody CarteiraObraRequest request) {
        return ResponseEntity.ok(useCases.informarCarteira(id, request));
    }

    @Override
    @PostMapping("/{id}/status-operacional")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<CarteiraObraResponse> informarStatus(@PathVariable UUID id,
                                                               @Valid @RequestBody StatusOperacionalObraRequest request) {
        return ResponseEntity.ok(useCases.informarStatusOperacional(id, request));
    }

    @Override
    @GetMapping("/{id}/historico-status")
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<List<HistoricoStatusObraResponse>> listarHistoricoStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.listarHistoricoStatus(id));
    }

    @Override
    @PostMapping("/{id}/deslocamento/recalcular")
    @PreAuthorize("hasAuthority('obras:obra:write')")
    public ResponseEntity<CarteiraObraResponse> recalcularDeslocamento(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.recalcularDeslocamento(id));
    }

    @Override
    @GetMapping("/{id}/orcamento")
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<OrcamentoObraResponse> buscarOrcamento(@PathVariable UUID id) {
        return ResponseEntity.ok(useCases.buscarOrcamento(id));
    }

    @Override
    @GetMapping("/{id}/proximas")
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<List<ObraProximaResponse>> listarObrasProximas(@PathVariable UUID id,
                                                                         @RequestParam(required = false) Double raioKm) {
        return ResponseEntity.ok(useCases.listarObrasProximas(id, raioKm));
    }

    @Override
    @GetMapping("/indicadores")
    @PreAuthorize("hasAuthority('obras:obra:read')")
    public ResponseEntity<IndicadoresCarteiraResponse> indicadores(@ParameterObject ObraFilter filter) {
        return ResponseEntity.ok(useCases.calcularIndicadores(filter));
    }
}
