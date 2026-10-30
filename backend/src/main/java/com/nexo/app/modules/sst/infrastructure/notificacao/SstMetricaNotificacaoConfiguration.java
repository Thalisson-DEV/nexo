package com.nexo.app.modules.sst.infrastructure.notificacao;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nexo.app.shared.application.notificacao.ProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho.MetricaDefinition;

import jakarta.persistence.EntityManager;

/**
 * O que o SST sabe medir para o motor de notificações — hoje, a fila de desvios
 * ainda não resolvidos, que é o caso de uso que motivou o modo por limiar.
 */
@Configuration(proxyBeanMethods = false)
class SstMetricaNotificacaoConfiguration {

    @Bean
    ProvedorMetricaGatilho sstProvedorMetricaGatilho(EntityManager entityManager) {
        return new JpaProvedorMetricaGatilho(entityManager, List.of(
                new MetricaDefinition("sst.desvios_pendentes", """
                        select count(d.id) from DesvioSeguranca d
                         where d.ativo = true
                           and d.status <> com.nexo.app.modules.sst.domain.StatusDesvio.RESOLVIDO
                        """)));
    }
}
