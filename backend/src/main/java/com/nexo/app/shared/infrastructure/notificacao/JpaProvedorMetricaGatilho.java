package com.nexo.app.shared.infrastructure.notificacao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.shared.application.notificacao.ProvedorMetricaGatilho;

import jakarta.persistence.EntityManager;

/**
 * Implementação reutilizável de {@link ProvedorMetricaGatilho} para métricas que se
 * resolvem em uma contagem JPQL.
 *
 * <p>Cada módulo declara apenas o par gatilho→consulta em uma {@code @Configuration}
 * e não precisa de mais nada. O JPQL vem sempre do código do servidor — nunca de
 * configuração feita em tempo de execução.</p>
 */
@Transactional(readOnly = true)
public class JpaProvedorMetricaGatilho implements ProvedorMetricaGatilho {

    private final EntityManager entityManager;
    private final Map<String, String> consultasPorGatilho;

    public JpaProvedorMetricaGatilho(EntityManager entityManager, List<MetricaDefinition> definicoes) {
        this.entityManager = entityManager;

        Map<String, String> indexadas = new LinkedHashMap<>();
        for (MetricaDefinition definicao : definicoes) {
            if (definicao.chaveGatilho() == null || definicao.chaveGatilho().isBlank()
                    || definicao.jpql() == null || definicao.jpql().isBlank()) {
                throw new IllegalArgumentException("Definição de métrica de gatilho inválida");
            }
            if (indexadas.put(definicao.chaveGatilho(), definicao.jpql()) != null) {
                throw new IllegalArgumentException(
                        "Métrica duplicada para o gatilho: " + definicao.chaveGatilho());
            }
        }
        this.consultasPorGatilho = Map.copyOf(indexadas);
    }

    @Override
    public List<String> chavesGatilho() {
        return List.copyOf(consultasPorGatilho.keySet());
    }

    @Override
    public long medir(String chaveGatilho) {
        String jpql = consultasPorGatilho.get(chaveGatilho);
        if (jpql == null) {
            throw new IllegalArgumentException("Métrica indisponível para o gatilho: " + chaveGatilho);
        }
        Long total = entityManager.createQuery(jpql, Long.class).getSingleResult();
        return total == null ? 0L : total;
    }

    public record MetricaDefinition(String chaveGatilho, String jpql) {
    }
}
