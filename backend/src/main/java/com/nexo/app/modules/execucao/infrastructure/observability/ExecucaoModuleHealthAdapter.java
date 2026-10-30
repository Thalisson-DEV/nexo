package com.nexo.app.modules.execucao.infrastructure.observability;

import com.nexo.app.modules.execucao.infrastructure.persistence.ExecucaoComercialRepository;
import com.nexo.app.modules.execucao.infrastructure.persistence.TurnoRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class ExecucaoModuleHealthAdapter implements ModuleHealthAdapter {
    private final ExecucaoComercialRepository repository;
    private final TurnoRepository turnoRepository;
    public ExecucaoModuleHealthAdapter(ExecucaoComercialRepository repository, TurnoRepository turnoRepository) {
        this.repository = repository;
        this.turnoRepository = turnoRepository;
    }
    @Override public String module() { return "execucao"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), () -> {
                    repository.findFirstByAtivoTrueOrderByIdAsc();
                    turnoRepository.findFirstByAtivoTrueOrderByIdAsc();
                });
    }
}
