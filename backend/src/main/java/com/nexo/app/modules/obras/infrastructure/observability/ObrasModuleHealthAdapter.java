package com.nexo.app.modules.obras.infrastructure.observability;

import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class ObrasModuleHealthAdapter implements ModuleHealthAdapter {
    private final ObraRepository repository;
    public ObrasModuleHealthAdapter(ObraRepository repository) { this.repository = repository; }
    @Override public String module() { return "obras"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
