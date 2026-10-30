package com.nexo.app.modules.chamado.infrastructure.observability;

import com.nexo.app.modules.chamado.infrastructure.persistence.ChamadoRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class ChamadoModuleHealthAdapter implements ModuleHealthAdapter {
    private final ChamadoRepository repository;
    public ChamadoModuleHealthAdapter(ChamadoRepository repository) { this.repository = repository; }
    @Override public String module() { return "chamado"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
