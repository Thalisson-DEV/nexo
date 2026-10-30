package com.nexo.app.modules.financeiro.infrastructure.web;

import com.nexo.app.modules.financeiro.application.dto.DashboardFinanceiroFilter;
import com.nexo.app.modules.financeiro.application.dto.DashboardFinanceiroResponse;
import com.nexo.app.modules.financeiro.application.usecases.DashboardFinanceiroUseCases;
import com.nexo.app.modules.financeiro.doc.DashboardFinanceiroDoc;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/financeiro/dashboards")
@RequiredArgsConstructor
public class DashboardFinanceiroController implements DashboardFinanceiroDoc {

    private final DashboardFinanceiroUseCases useCases;

    @Override
    @GetMapping("/executivo")
    @PreAuthorize("hasAuthority('financeiro:dashboard:read')")
    public ResponseEntity<DashboardFinanceiroResponse> dashboard(@ParameterObject DashboardFinanceiroFilter filter) {
        return ResponseEntity.ok(useCases.gerar(filter));
    }
}
