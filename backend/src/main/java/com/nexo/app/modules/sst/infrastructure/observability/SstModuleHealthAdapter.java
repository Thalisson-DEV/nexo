package com.nexo.app.modules.sst.infrastructure.observability;

import com.nexo.app.modules.sst.infrastructure.persistence.DesvioSegurancaRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.InspecaoSegurancaRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class SstModuleHealthAdapter implements ModuleHealthAdapter {
    private final DesvioSegurancaRepository repository;
    private final InspecaoSegurancaRepository inspecaoRepository;
    public SstModuleHealthAdapter(DesvioSegurancaRepository repository, InspecaoSegurancaRepository inspecaoRepository) {
        this.repository = repository;
        this.inspecaoRepository = inspecaoRepository;
    }
    @Override public String module() { return "sst"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), () -> {
                    repository.findFirstByAtivoTrueOrderByIdAsc();
                    inspecaoRepository.findFirstByAtivoTrueOrderByIdAsc();
                });
    }
}
