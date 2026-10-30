package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.domain.LogSistema;
import com.nexo.app.modules.iam.infrastructure.persistence.LogSistemaRepository;
import com.nexo.app.shared.domain.events.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final LogSistemaRepository repository;

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditEvent(AuditEvent event) {
        log.debug("Recebendo evento de auditoria para a tabela: {}", event.tabela());
        
        LogSistema logSistema = new LogSistema(
            event.tabela(),
            event.registroId(),
            event.operacao(),
            event.dadosAnteriores(),
            event.dadosNovos()
        );
        
        salvarLog(logSistema);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void salvarLog(LogSistema logSistema) {
        try {
            repository.save(logSistema);
        } catch (Exception e) {
            log.error("Erro ao persistir log de auditoria: {}", e.getMessage());
        }
    }
}
