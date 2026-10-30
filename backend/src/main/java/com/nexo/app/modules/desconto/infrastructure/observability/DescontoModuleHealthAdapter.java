package com.nexo.app.modules.desconto.infrastructure.observability;

import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class DescontoModuleHealthAdapter implements ModuleHealthAdapter {
    private final DescontoRepository repository;
    public DescontoModuleHealthAdapter(DescontoRepository repository) { this.repository = repository; }
    @Override public String module() { return "desconto"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
