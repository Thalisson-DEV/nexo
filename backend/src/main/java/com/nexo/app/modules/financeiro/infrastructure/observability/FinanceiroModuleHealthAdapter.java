package com.nexo.app.modules.financeiro.infrastructure.observability;

import com.nexo.app.modules.financeiro.infrastructure.persistence.SolicitacaoPagamentoRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class FinanceiroModuleHealthAdapter implements ModuleHealthAdapter {
    private final SolicitacaoPagamentoRepository repository;
    public FinanceiroModuleHealthAdapter(SolicitacaoPagamentoRepository repository) { this.repository = repository; }
    @Override public String module() { return "financeiro"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
