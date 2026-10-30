package com.nexo.app.modules.desconto.infrastructure.web;

import com.nexo.app.modules.desconto.application.dto.DashboardDescontoFilter;
import com.nexo.app.modules.desconto.application.dto.DashboardDescontosResponse;
import com.nexo.app.modules.desconto.application.usecases.DashboardDescontoUseCases;
import com.nexo.app.modules.desconto.doc.DashboardDescontoDoc;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/descontos/dashboards")
@RequiredArgsConstructor
public class DashboardDescontoController implements DashboardDescontoDoc {

    private final DashboardDescontoUseCases useCases;

    @Override
    @GetMapping("/executivo")
    @PreAuthorize("hasAuthority('desconto:dashboard:read')")
    public ResponseEntity<DashboardDescontosResponse> dashboard(@ParameterObject DashboardDescontoFilter filter) {
        return ResponseEntity.ok(useCases.gerar(filter));
    }
}
