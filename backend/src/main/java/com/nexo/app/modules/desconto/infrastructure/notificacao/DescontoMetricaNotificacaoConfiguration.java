package com.nexo.app.modules.desconto.infrastructure.notificacao;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nexo.app.shared.application.notificacao.ProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho.MetricaDefinition;

import jakarta.persistence.EntityManager;

/**
 * O que o módulo de descontos sabe medir para o motor de notificações.
 *
 * <p>A consulta vive aqui, no dono do domínio, e não na configuração da regra: o
 * administrador escolhe o limiar e os destinatários, nunca o SQL.</p>
 */
@Configuration(proxyBeanMethods = false)
class DescontoMetricaNotificacaoConfiguration {

    @Bean
    ProvedorMetricaGatilho descontoProvedorMetricaGatilho(EntityManager entityManager) {
        return new JpaProvedorMetricaGatilho(entityManager, List.of(
                new MetricaDefinition("desconto.pendente_aprovacao", """
                        select count(d.id) from Desconto d
                         where d.ativo = true
                           and d.status = com.nexo.app.modules.desconto.domain.StatusDesconto.SOLICITADO
                        """)));
    }
}
