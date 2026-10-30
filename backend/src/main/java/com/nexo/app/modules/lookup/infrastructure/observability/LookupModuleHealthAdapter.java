package com.nexo.app.modules.lookup.infrastructure.observability;

import com.nexo.app.modules.estoque.EstoqueLookupApi;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

/** Exercises only the finite lookup sources; it deliberately bypasses the cache-writing use case. */
@Component
public final class LookupModuleHealthAdapter implements ModuleHealthAdapter {
    private final BaseOperacionalApi bases;
    private final ProcessoNegocioApi processos;
    private final EstoqueLookupApi estoque;
    public LookupModuleHealthAdapter(BaseOperacionalApi bases, ProcessoNegocioApi processos, EstoqueLookupApi estoque) {
        this.bases = bases;
        this.processos = processos;
        this.estoque = estoque;
    }
    @Override public String module() { return "lookup"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database", "rh-lookups", "estoque-lookups"), () -> {
                    bases.listarOpcoesAtivas();
                    processos.listarOpcoesAtivas();
                    estoque.listarStatusSolicitacao();
                });
    }
}
