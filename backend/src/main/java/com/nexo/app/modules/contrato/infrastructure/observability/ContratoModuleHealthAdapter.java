package com.nexo.app.modules.contrato.infrastructure.observability;

import com.nexo.app.modules.contrato.infrastructure.persistence.ContratoRepository;
import com.nexo.app.modules.contrato.infrastructure.persistence.AcaoRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class ContratoModuleHealthAdapter implements ModuleHealthAdapter {
    private final ContratoRepository repository;
    private final AcaoRepository acaoRepository;
    public ContratoModuleHealthAdapter(ContratoRepository repository, AcaoRepository acaoRepository) {
        this.repository = repository;
        this.acaoRepository = acaoRepository;
    }
    @Override public String module() { return "contrato"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), () -> {
                    repository.findFirstByAtivoTrueOrderByIdAsc();
                    acaoRepository.findFirstByAtivoTrueOrderByIdAsc();
                });
    }
}
