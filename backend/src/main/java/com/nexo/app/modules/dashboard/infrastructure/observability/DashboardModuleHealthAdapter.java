package com.nexo.app.modules.dashboard.infrastructure.observability;

import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

/** Uses the dashboard's bounded lookup path with a synthetic key and discards its result. */
@Component
public final class DashboardModuleHealthAdapter implements ModuleHealthAdapter {
    private static final String PROBE_CODE = "__ces_observability_probe__";
    private final EquipeApi equipeApi;
    public DashboardModuleHealthAdapter(EquipeApi equipeApi) { this.equipeApi = equipeApi; }
    @Override public String module() { return "dashboard"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database", "rh-equipes"), () -> equipeApi.existePorCodigo(PROBE_CODE));
    }
}
