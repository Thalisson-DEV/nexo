package com.nexo.app.shared.infrastructure.persistence;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.events.AuditEvent;
import com.nexo.app.shared.infrastructure.config.ApplicationContextUtils;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Captura o estado das entidades nos eventos do ciclo de vida JPA e publica o
 * {@link AuditEvent} que alimenta a trilha imutável em {@code logs_sistema}.
 *
 * <p>Os snapshots são gerados pelo {@link EntitySnapshotFactory}, que trabalha em
 * profundidade 1. Nenhum relacionamento é percorrido, o que elimina a recursão infinita
 * das relações bidirecionais e impede que a auditoria dispare carregamento LAZY.</p>
 */
@Slf4j
public class AuditListener {

    private static final String ENTIDADE_LOG = "LogSistema";

    @PostLoad
    public void onPostLoad(Object entity) {
        if (!(entity instanceof BaseEntity baseEntity) || ehEntidadeDeLog(entity)) return;

        try {
            baseEntity.setSnapshotAnterior(EntitySnapshotFactory.criar(entity));
        } catch (Exception e) {
            log.error("Erro ao gerar snapshot anterior de {}", nomeDaEntidade(entity), e);
        }
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        audit(entity, "INSERT", null);
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        Map<String, Object> anterior = entity instanceof BaseEntity baseEntity
                ? baseEntity.getSnapshotAnterior()
                : null;
        audit(entity, "UPDATE", anterior);
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        Map<String, Object> anterior = entity instanceof BaseEntity
                ? EntitySnapshotFactory.criar(entity)
                : null;
        audit(entity, "DELETE", anterior);
    }

    private void audit(Object entity, String operacao, Map<String, Object> dadosAnteriores) {
        if (!(entity instanceof BaseEntity baseEntity)) return;
        if (ehEntidadeDeLog(entity)) return;

        try {
            ApplicationContext context = ApplicationContextUtils.getContext();
            if (context == null) {
                log.warn("Auditoria ignorada para {}: Contexto Spring ainda não inicializado.", nomeDaEntidade(entity));
                return;
            }

            Class<?> tipo = Hibernate.getClass(entity);
            String tableName = tipo.isAnnotationPresent(Table.class)
                    ? tipo.getAnnotation(Table.class).name()
                    : tipo.getSimpleName();

            Map<String, Object> snapshotNovo = "DELETE".equals(operacao)
                    ? null
                    : EntitySnapshotFactory.criar(entity);

            AuditEvent event = new AuditEvent(
                tableName,
                baseEntity.getId(),
                operacao,
                dadosAnteriores,
                snapshotNovo
            );

            // O próprio ApplicationContext é o publicador de eventos
            context.publishEvent(event);
        } catch (Exception e) {
            log.error("FALHA NA AUDITORIA: {} ao processar entidade {}", e.getMessage(), nomeDaEntidade(entity));
        }
    }

    /**
     * A entidade de auditoria não se audita, sob pena de loop infinito de eventos.
     */
    private boolean ehEntidadeDeLog(Object entity) {
        return ENTIDADE_LOG.equals(nomeDaEntidade(entity));
    }

    private String nomeDaEntidade(Object entity) {
        return Hibernate.getClass(entity).getSimpleName();
    }
}
