package com.nexo.app.modules.chamado.infrastructure.web;

import com.nexo.app.modules.chamado.application.dto.DashboardChamadosFilter;
import com.nexo.app.modules.chamado.application.dto.DashboardChamadosResponse;
import com.nexo.app.modules.chamado.application.usecases.DashboardChamadosUseCases;
import com.nexo.app.modules.chamado.doc.DashboardChamadosControllerDoc;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/chamados/dashboards")
@RequiredArgsConstructor
public class DashboardChamadosController implements DashboardChamadosControllerDoc {

    private final DashboardChamadosUseCases useCases;

    @Override
    @GetMapping("/gerencial")
    @PreAuthorize("hasAnyAuthority('chamado:manage', 'chamado:dashboard:read')")
    public ResponseEntity<DashboardChamadosResponse> dashboard(@ParameterObject DashboardChamadosFilter filter) {
        return ResponseEntity.ok(useCases.gerar(filter));
    }
}
