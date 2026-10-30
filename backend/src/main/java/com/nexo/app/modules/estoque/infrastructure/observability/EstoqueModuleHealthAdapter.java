package com.nexo.app.modules.estoque.infrastructure.observability;

import com.nexo.app.modules.estoque.infrastructure.persistence.MaterialRepository;
import com.nexo.app.modules.estoque.infrastructure.persistence.SaldoRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class EstoqueModuleHealthAdapter implements ModuleHealthAdapter {
    private final MaterialRepository repository;
    private final SaldoRepository saldoRepository;
    public EstoqueModuleHealthAdapter(MaterialRepository repository, SaldoRepository saldoRepository) {
        this.repository = repository;
        this.saldoRepository = saldoRepository;
    }
    @Override public String module() { return "estoque"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database"), () -> {
                    repository.findFirstByAtivoTrueOrderByIdAsc();
                    saldoRepository.findFirstByAtivoTrueOrderByIdAsc();
                });
    }
}
