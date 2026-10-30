package com.nexo.app.modules.frota.infrastructure.web;

import com.nexo.app.modules.frota.application.dto.JustificativaRequest;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroFilter;
import com.nexo.app.modules.frota.application.dto.LeituraOdometroResponse;
import com.nexo.app.modules.frota.application.usecases.OdometroUseCases;
import com.nexo.app.modules.frota.doc.OdometroFrotaControllerDoc;
import com.nexo.app.shared.application.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/frota/odometro")
@RequiredArgsConstructor
public class OdometroFrotaController implements OdometroFrotaControllerDoc {

    private final OdometroUseCases odometro;

    @Override
    @GetMapping
    @PreAuthorize("hasAuthority('frota:veiculo:read')")
    public ResponseEntity<PageResponse<LeituraOdometroResponse>> listar(@ParameterObject LeituraOdometroFilter filtro,
                                                                       @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(odometro.listar(filtro, pageable));
    }

    @Override
    @PostMapping("/{id}/anulacao")
    @PreAuthorize("hasAuthority('frota:odometro:anular')")
    public ResponseEntity<LeituraOdometroResponse> anular(@PathVariable UUID id,
                                                          @Valid @RequestBody JustificativaRequest request) {
        return ResponseEntity.ok(odometro.anular(id, request.justificativa()));
    }

    @Override
    @PostMapping("/{id}/confirmacao")
    @PreAuthorize("hasAuthority('frota:odometro:anular')")
    public ResponseEntity<LeituraOdometroResponse> confirmar(@PathVariable UUID id,
                                                             @Valid @RequestBody JustificativaRequest request) {
        return ResponseEntity.ok(odometro.confirmar(id, request.justificativa()));
    }
}
