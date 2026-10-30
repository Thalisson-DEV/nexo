package com.nexo.app.modules.mencao.infrastructure.observability;

import com.nexo.app.modules.mencao.infrastructure.persistence.MencaoRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class MencaoModuleHealthAdapter implements ModuleHealthAdapter {
    private final MencaoRepository repository;
    public MencaoModuleHealthAdapter(MencaoRepository repository) { this.repository = repository; }
    @Override public String module() { return "mencao"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database", "mention-handler"), repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
