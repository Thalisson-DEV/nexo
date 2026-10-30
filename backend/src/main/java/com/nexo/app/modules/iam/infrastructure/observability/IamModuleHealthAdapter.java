package com.nexo.app.modules.iam.infrastructure.observability;

import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthAdapter;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthCheckContext;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthCoverage;
import com.nexo.app.shared.infrastructure.observability.ModuleHealthResult;
import com.nexo.app.shared.infrastructure.observability.ReadOnlyModuleHealthProbe;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class IamModuleHealthAdapter implements ModuleHealthAdapter {
    private final UsuarioRepository repository;

    public IamModuleHealthAdapter(UsuarioRepository repository) {
        this.repository = repository;
    }

    @Override public String module() { return "iam"; }

    @Override
    public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database", "authentication-config"), repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
