package com.nexo.app.modules.notificacao.application.usecases;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexo.app.modules.notificacao.application.dto.NovaNotificacao;
import com.nexo.app.modules.notificacao.domain.CondicaoRegra;
import com.nexo.app.modules.notificacao.domain.ExecucaoRegra;
import com.nexo.app.modules.notificacao.domain.OrigemNotificacao;
import com.nexo.app.modules.notificacao.domain.RegraNotificacao;
import com.nexo.app.modules.notificacao.infrastructure.persistence.ExecucaoRegraRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica UMA regra, em transação própria.
 *
 * <p>O {@code REQUIRES_NEW} é o que torna real a promessa de que uma regra mal
 * configurada não cala as demais. Capturar a exceção no laço não bastaria: numa
 * transação compartilhada, a falha a marca como <em>rollback-only</em> e derruba no
 * commit tudo o que as outras regras já tinham gravado.</p>
 *
 * <p>A regra é recarregada aqui pelo id, e não recebida pronta, porque quem a
 * selecionou estava em outra transação — ou em nenhuma, no caso da varredura
 * agendada. Uma entidade desanexada estouraria ao tocar as coleções de condições e
 * destinatários.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AplicadorRegra {

    private final RegraNotificacaoRepository regraRepository;
    private final ExecucaoRegraRepository execucaoRepository;
    private final ResolvedorDestinatariosRegra resolvedorDestinatarios;
    private final RenderizadorTemplate renderizador;
    private final PublicadorNotificacoes publicador;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int aplicar(UUID idRegra, Map<String, Object> contexto, Map<String, Set<UUID>> papeis,
                       UUID idReferencia, String escopo, Long valorLimiar) {
        try {
            return executar(idRegra, contexto, papeis, idReferencia, escopo, valorLimiar);
        } catch (RuntimeException e) {
            log.error("Falha ao aplicar a regra de notificação {}.", idRegra, e);
            return 0;
        }
    }

    private int executar(UUID idRegra, Map<String, Object> contexto, Map<String, Set<UUID>> papeis,
                         UUID idReferencia, String escopo, Long valorLimiar) {
        RegraNotificacao regra = regraRepository.findById(idRegra).orElse(null);
        if (regra == null || !regra.isAtivo()) {
            return 0;
        }
        if (valorLimiar != null) {
            if (!atingiuLimiar(regra, valorLimiar)) {
                return 0;
            }
            // O limiar só é conhecido depois de carregar a regra, mas os textos podem
            // usá-lo ("acima do limite de {{limiar}}"), então entra no contexto aqui.
            contexto = new HashMap<>(contexto);
            contexto.put("limiar", regra.getLimiar());
        }
        if (!condicoesSatisfeitas(regra, contexto)) {
            return 0;
        }

        OffsetDateTime agora = OffsetDateTime.now();
        ExecucaoRegra execucao = execucaoRepository
                .findByIdRegraAndChaveEscopo(idRegra, escopo)
                .orElse(null);
        if (execucao != null && execucao.emCooldown(regra.getCooldownMinutos(), agora)) {
            log.debug("Regra '{}' ainda em silêncio para o escopo {}.", regra.getNome(), escopo);
            return 0;
        }

        Set<UUID> destinatarios = resolvedorDestinatarios.resolver(regra, contexto, papeis);
        if (destinatarios.isEmpty()) {
            log.debug("Regra '{}' não alcançou nenhum destinatário.", regra.getNome());
            return 0;
        }

        int entregues = publicador.publicar(NovaNotificacao.builder()
                .destinatarios(destinatarios)
                .origem(OrigemNotificacao.REGRA)
                .idRegra(idRegra)
                .idReferencia(idReferencia)
                .categoria(regra.getCategoria())
                .prioridade(regra.getPrioridade())
                .titulo(renderizador.renderizar(regra.getTituloTemplate(), contexto))
                .corpo(renderizador.renderizar(regra.getCorpoTemplate(), contexto))
                .deeplink(renderizador.renderizar(regra.getDeeplinkTemplate(), contexto))
                .contexto(contexto)
                .chaveIdempotencia(chaveIdempotencia(idRegra, escopo, agora))
                .email(regra.isEnviarEmail())
                .chaveGatilho(regra.getChaveGatilho())
                .build());

        registrarExecucao(idRegra, escopo, agora, execucao);
        return entregues;
    }

    private boolean atingiuLimiar(RegraNotificacao regra, long valorAtual) {
        if (regra.getLimiar() == null) {
            log.warn("Regra '{}' é por limiar mas não tem limiar definido; ignorada.", regra.getNome());
            return false;
        }
        return valorAtual >= regra.getLimiar();
    }

    /**
     * Numa regra por ocorrência a chave é regra + registro, então o reprocessamento do
     * outbox não duplica o aviso sobre o mesmo desconto. Numa regra por limiar não
     * existe registro, e reavisar depois do silêncio é o comportamento desejado — ali
     * quem faz a vez da idempotência é o cooldown, e a chave leva o instante para não
     * bloquear os avisos seguintes.
     */
    private String chaveIdempotencia(UUID idRegra, String escopo, OffsetDateTime agora) {
        if (ExecucaoRegra.ESCOPO_GLOBAL.equals(escopo)) {
            return "regra:" + idRegra + ":" + agora.toEpochSecond();
        }
        return "regra:" + idRegra + ":" + escopo;
    }

    private void registrarExecucao(UUID idRegra, String escopo,
                                   OffsetDateTime agora, ExecucaoRegra existente) {
        if (existente == null) {
            execucaoRepository.save(new ExecucaoRegra(idRegra, escopo, agora));
            return;
        }
        existente.registrar(agora);
        execucaoRepository.save(existente);
    }

    private boolean condicoesSatisfeitas(RegraNotificacao regra, Map<String, Object> contexto) {
        for (CondicaoRegra condicao : regra.condicoesAtivas()) {
            Object valor = contexto == null ? null : contexto.get(condicao.getCampo());
            if (!condicao.satisfeitaPor(valor)) {
                return false;
            }
        }
        return true;
    }
}
