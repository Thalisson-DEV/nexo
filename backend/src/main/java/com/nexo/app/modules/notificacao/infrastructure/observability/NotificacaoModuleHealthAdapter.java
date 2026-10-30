package com.nexo.app.modules.notificacao.infrastructure.observability;

import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ExecucaoRegraRepository;
import com.nexo.app.shared.infrastructure.observability.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public final class NotificacaoModuleHealthAdapter implements ModuleHealthAdapter {
    private final RegraNotificacaoRepository repository;
    private final ExecucaoRegraRepository execucaoRepository;
    public NotificacaoModuleHealthAdapter(RegraNotificacaoRepository repository, ExecucaoRegraRepository execucaoRepository) {
        this.repository = repository;
        this.execucaoRepository = execucaoRepository;
    }
    @Override public String module() { return "notificacao"; }
    @Override public ModuleHealthResult check(ModuleHealthCheckContext context) {
        return ReadOnlyModuleHealthProbe.execute(module(), context, ModuleHealthCoverage.FUNCTIONAL_READ,
                List.of("database", "notification-executor"), () -> {
                    repository.findFirstByAtivoTrueOrderByIdAsc();
                    execucaoRepository.findFirstByAtivoTrueOrderByIdAsc();
                });
    }
}
