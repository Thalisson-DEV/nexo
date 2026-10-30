package com.nexo.app.modules.chamado.infrastructure.notificacao;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nexo.app.shared.application.notificacao.ProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho.MetricaDefinition;

import jakarta.persistence.EntityManager;

/**
 * O que a central de chamados sabe medir para o motor de notificações: a fila que
 * ainda não chegou a uma resolução.
 */
@Configuration(proxyBeanMethods = false)
class ChamadoMetricaNotificacaoConfiguration {

    @Bean
    ProvedorMetricaGatilho chamadoProvedorMetricaGatilho(EntityManager entityManager) {
        return new JpaProvedorMetricaGatilho(entityManager, List.of(
                new MetricaDefinition("chamado.pendentes_atendimento", """
                        select count(c.id) from Chamado c
                         where c.ativo = true
                           and c.status in (
                                com.nexo.app.modules.chamado.domain.StatusChamado.ABERTO,
                                com.nexo.app.modules.chamado.domain.StatusChamado.EM_ATENDIMENTO,
                                com.nexo.app.modules.chamado.domain.StatusChamado.AGUARDANDO_SOLICITANTE)
                        """)));
    }
}
