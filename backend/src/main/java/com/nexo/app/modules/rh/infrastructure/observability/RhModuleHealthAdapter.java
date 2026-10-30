package com.nexo.app.modules.rh.infrastructure.observability;

import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class RhModuleHealthAdapter implements ModuleHealthAdapter {
    private final ColaboradorRepository repository;
    public RhModuleHealthAdapter(ColaboradorRepository repository) { this.repository = repository; }
    @Override public String module() { return "rh"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
