package com.nexo.app.modules.assistente.infrastructure.observability;

import com.nexo.app.modules.assistente.infrastructure.persistence.ConversaAssistenteRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

/** Configuration probe only: an enabled flag never claims the external model is reachable. */
@Component
public final class AssistenteModuleHealthAdapter implements ModuleHealthAdapter {
    private final ConversaAssistenteRepository repository;
    private final boolean enabled;
    public AssistenteModuleHealthAdapter(ConversaAssistenteRepository repository,
                                         @Value("${ces.assistente.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.enabled = enabled;
    }
    @Override public String module() { return "assistente"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        if (!enabled) {
            return new ModuleHealthResult(module(), ModuleHealthStatus.DISABLED,
                    Instant.now(context.clock()), 0, ModuleHealthReason.DISABLED,
                    ModuleHealthCoverage.CONFIGURATION_ONLY,
                    List.of("assistant-config", "llm-provider-config"));
        }
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.CONFIGURATION_ONLY,
                List.of("database", "assistant-config", "llm-provider-config"),
                repository::findFirstByAtivoTrueOrderByIdAsc);
    }
}
