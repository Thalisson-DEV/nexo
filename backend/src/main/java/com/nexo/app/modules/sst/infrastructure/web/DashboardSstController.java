package com.nexo.app.modules.sst.infrastructure.web;

import com.nexo.app.modules.sst.application.dto.DashboardDesviosResponse;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstFilter;
import com.nexo.app.modules.sst.application.dto.DashboardEventosSstResponse;
import com.nexo.app.modules.sst.application.dto.DashboardInspecoesResponse;
import com.nexo.app.modules.sst.application.dto.DashboardSstFilter;
import com.nexo.app.modules.sst.application.usecases.DashboardEventosSstUseCases;
import com.nexo.app.modules.sst.application.usecases.DashboardSstUseCases;
import com.nexo.app.modules.sst.doc.DashboardSstControllerDoc;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sst/dashboards")
@RequiredArgsConstructor
public class DashboardSstController implements DashboardSstControllerDoc {

    private final DashboardSstUseCases useCases;
    private final DashboardEventosSstUseCases eventosUseCases;

    @Override
    @GetMapping("/inspecoes")
    @PreAuthorize("hasAuthority('sst:dashboard:read')")
    public ResponseEntity<DashboardInspecoesResponse> dashboardInspecoes(@ParameterObject DashboardSstFilter filter) {
        return ResponseEntity.ok(useCases.gerarDashboardInspecoes(filter));
    }

    @Override
    @GetMapping("/desvios")
    @PreAuthorize("hasAuthority('sst:dashboard:read')")
    public ResponseEntity<DashboardDesviosResponse> dashboardDesvios(@ParameterObject DashboardSstFilter filter) {
        return ResponseEntity.ok(useCases.gerarDashboardDesvios(filter));
    }

    @Override
    @GetMapping("/eventos")
    @PreAuthorize("hasAuthority('sst:dashboard:read')")
    public ResponseEntity<DashboardEventosSstResponse> dashboardEventos(
            @ParameterObject DashboardEventosSstFilter filter) {
        return ResponseEntity.ok(eventosUseCases.gerar(filter));
    }
}
