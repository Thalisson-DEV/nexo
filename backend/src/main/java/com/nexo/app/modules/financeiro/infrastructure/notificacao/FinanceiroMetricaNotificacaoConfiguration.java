package com.nexo.app.modules.financeiro.infrastructure.notificacao;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nexo.app.shared.application.notificacao.ProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho;
import com.nexo.app.shared.infrastructure.notificacao.JpaProvedorMetricaGatilho.MetricaDefinition;

import jakarta.persistence.EntityManager;

/**
 * O que o financeiro sabe medir para o motor de notificações: compras e pagamentos
 * submetidos e ainda sem decisão, somados — do ponto de vista de quem aprova, é uma
 * fila só.
 */
@Configuration(proxyBeanMethods = false)
class FinanceiroMetricaNotificacaoConfiguration {

    @Bean
    ProvedorMetricaGatilho financeiroProvedorMetricaGatilho(EntityManager entityManager) {
        return new JpaProvedorMetricaGatilho(entityManager, List.of(
                new MetricaDefinition("financeiro.solicitacoes_pendentes", """
                        select (
                            (select count(c.id) from SolicitacaoCompra c
                              where c.ativo = true
                                and c.status = com.nexo.app.modules.financeiro.domain.StatusSolicitacao.SOLICITADO)
                          + (select count(p.id) from SolicitacaoPagamento p
                              where p.ativo = true
                                and p.status = com.nexo.app.modules.financeiro.domain.StatusSolicitacao.SOLICITADO)
                        )
                        """)));
    }
}
