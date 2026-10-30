package com.nexo.app.modules.assistente.application.agent;

import com.nexo.app.modules.assistente.application.AssistenteProperties;
import com.nexo.app.shared.domain.exception.RequisicaoDuplicadaException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro em memoria das execucoes ativas (uma por conversa). Execucoes concluidas ficam disponiveis
 * por alguns minutos para retomada do stream e depois sao descartadas; o historico definitivo esta no
 * banco. Em implantacao com varias instancias, o sinal de parada deve migrar para Redis.
 */
@Component
public class AgentRunRegistry {

    private static final Duration RETENCAO_APOS_FIM = Duration.ofMinutes(10);

    private final Map<UUID, AgentRun> porConversa = new ConcurrentHashMap<>();
    private final Map<UUID, AgentRun> porId = new ConcurrentHashMap<>();
    private final AssistenteProperties properties;

    public AgentRunRegistry(AssistenteProperties properties) {
        this.properties = properties;
    }

    public synchronized AgentRun registrar(AgentRun run) {
        AgentRun atual = porConversa.get(run.conversaId());
        if (atual != null && !atual.isFinished()) {
            throw new RequisicaoDuplicadaException(
                    "Já existe uma resposta em andamento nesta conversa. Aguarde ou interrompa a geração.");
        }
        long ativos = porId.values().stream().filter(item -> !item.isFinished()).count();
        if (ativos >= Math.max(1, properties.getAgent().getMaxConcurrentRuns())) {
            throw new IllegalStateException("O Copiloto está com muitas análises em andamento. Tente novamente em instantes.");
        }
        porConversa.put(run.conversaId(), run);
        porId.put(run.id(), run);
        return run;
    }

    public Optional<AgentRun> daConversa(UUID conversaId) {
        return Optional.ofNullable(porConversa.get(conversaId));
    }

    public Optional<AgentRun> ativaDaConversa(UUID conversaId) {
        return daConversa(conversaId).filter(run -> !run.isFinished());
    }

    public boolean possuiExecucaoAtiva(UUID mensagemAssistenteId) {
        return porId.values().stream()
                .anyMatch(run -> !run.isFinished() && run.mensagemAssistenteId().equals(mensagemAssistenteId));
    }

    public long ativas() {
        return porId.values().stream().filter(run -> !run.isFinished()).count();
    }

    @Scheduled(fixedDelay = 60_000)
    public void limparConcluidas() {
        Instant limite = Instant.now().minus(RETENCAO_APOS_FIM);
        porId.values().removeIf(run -> run.isFinished() && run.finalizadoEm() != null
                && run.finalizadoEm().isBefore(limite));
        porConversa.values().removeIf(run -> run.isFinished() && run.finalizadoEm() != null
                && run.finalizadoEm().isBefore(limite));
    }
}
