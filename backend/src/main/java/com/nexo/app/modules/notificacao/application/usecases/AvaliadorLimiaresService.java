package com.nexo.app.modules.notificacao.application.usecases;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nexo.app.modules.notificacao.domain.GatilhoNotificacao;
import com.nexo.app.modules.notificacao.domain.ModoGatilho;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository;
import com.nexo.app.modules.notificacao.infrastructure.persistence.RegraNotificacaoRepository.RegraPorGatilho;
import com.nexo.app.shared.application.notificacao.ProvedorMetricaGatilho;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Varredura periódica das regras por limiar.
 *
 * <p>Existe porque situações não se anunciam. "Há 12 desvios pendentes" continua
 * verdadeiro amanhã sem que nenhum evento seja emitido, então a única forma de
 * notificar sobre isso é perguntar de tempos em tempos. O que impede a varredura de
 * virar spam é o cooldown de cada regra, verificado dentro do motor.</p>
 *
 * <p>A medição é feita uma vez por gatilho, não uma por regra: várias regras podem
 * observar a mesma fila com limiares e destinatários diferentes (o coordenador quer
 * saber a partir de 5, o diretor a partir de 30) e não faz sentido contar duas
 * vezes.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvaliadorLimiaresService {

    private final RegraNotificacaoRepository regraRepository;
    private final MotorNotificacoes motor;
    private final List<ProvedorMetricaGatilho> provedores;

    @Scheduled(cron = "${ces.notificacao.limiares-cron:0 */15 * * * *}")
    public void avaliar() {
        Map<String, ProvedorMetricaGatilho> provedorPorGatilho = indexarProvedores();
        if (provedorPorGatilho.isEmpty()) {
            return;
        }

        List<String> chavesComProvedor = chavesPorLimiarCom(provedorPorGatilho);
        List<RegraPorGatilho> regras = regraRepository.buscarAtivasPorGatilhos(chavesComProvedor);
        if (regras.isEmpty()) {
            return;
        }

        Map<String, Long> medicoes = new HashMap<>();
        int entregues = 0;

        for (RegraPorGatilho regra : regras) {
            String chave = regra.getChaveGatilho();
            Long valor = medicoes.computeIfAbsent(chave,
                    gatilho -> medir(provedorPorGatilho.get(gatilho), gatilho));
            if (valor == null) {
                continue;
            }
            entregues += motor.avaliarLimiar(regra.getId(), valor);
        }

        if (entregues > 0) {
            log.info("Varredura de limiares entregou {} notificações.", entregues);
        }
    }

    /**
     * Uma medição que falha (módulo indisponível, consulta quebrada) não pode
     * interromper a avaliação dos outros gatilhos.
     */
    private Long medir(ProvedorMetricaGatilho provedor, String chaveGatilho) {
        try {
            return provedor.medir(chaveGatilho);
        } catch (RuntimeException e) {
            log.error("Falha ao medir o gatilho '{}'.", chaveGatilho, e);
            return null;
        }
    }

    private List<String> chavesPorLimiarCom(Map<String, ProvedorMetricaGatilho> provedorPorGatilho) {
        List<String> chaves = new ArrayList<>();
        for (GatilhoNotificacao gatilho : GatilhoNotificacao.values()) {
            if (gatilho.getModo() == ModoGatilho.POR_LIMIAR
                    && provedorPorGatilho.containsKey(gatilho.getChave())) {
                chaves.add(gatilho.getChave());
            }
        }
        return chaves;
    }

    private Map<String, ProvedorMetricaGatilho> indexarProvedores() {
        Map<String, ProvedorMetricaGatilho> indice = new HashMap<>();
        for (ProvedorMetricaGatilho provedor : provedores) {
            for (String chave : provedor.chavesGatilho()) {
                ProvedorMetricaGatilho anterior = indice.put(chave, provedor);
                if (anterior != null) {
                    log.warn("Mais de um módulo mede o gatilho '{}'; o último registrado prevalece.", chave);
                }
            }
        }
        return indice;
    }
}
