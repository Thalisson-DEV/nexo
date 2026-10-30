package com.nexo.app.modules.automacao.application.usecases;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexo.app.modules.automacao.application.motor.Recorrencia;
import com.nexo.app.modules.automacao.domain.Automacao;
import com.nexo.app.modules.automacao.domain.ExecucaoAutomacao;
import com.nexo.app.modules.automacao.domain.ModoExecucao;
import com.nexo.app.modules.automacao.domain.StatusExecucao;
import com.nexo.app.modules.automacao.domain.VersaoAutomacao;
import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.ArquivoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.AutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.ExecucaoAutomacaoRepository;
import com.nexo.app.modules.automacao.infrastructure.persistence.VersaoAutomacaoRepository;
import com.nexo.app.shared.application.notificacao.ProvedorMetricaGatilho;

/**
 * O relógio do motor. A cada meio minuto: dispara as agendas vencidas, mede os
 * limiares, devolve à fila as execuções que venceram a espera ou foram abandonadas por
 * um servidor que caiu, e encerra as que passaram do tempo limite.
 */
@Service
public class AgendadorAutomacoes {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgendadorAutomacoes.class);

    private final AutomacaoRepository automacoes;
    private final VersaoAutomacaoRepository versoes;
    private final ExecucaoAutomacaoRepository execucoes;
    private final ArquivoAutomacaoRepository arquivos;
    private final ExecucaoAutomacoesService fila;
    private final List<ProvedorMetricaGatilho> provedores;
    private final TransactionTemplate transacao;

    public AgendadorAutomacoes(AutomacaoRepository automacoes, VersaoAutomacaoRepository versoes,
            ExecucaoAutomacaoRepository execucoes, ArquivoAutomacaoRepository arquivos, ExecucaoAutomacoesService fila,
            List<ProvedorMetricaGatilho> provedores, TransactionTemplate transacao) {
        this.automacoes = automacoes;
        this.versoes = versoes;
        this.execucoes = execucoes;
        this.arquivos = arquivos;
        this.fila = fila;
        this.provedores = provedores;
        this.transacao = transacao;
    }

    @Scheduled(fixedDelayString = "${ces.automacao.tick-ms:30000}", initialDelayString = "${ces.automacao.tick-inicial-ms:20000}")
    public void tick() {
        OffsetDateTime agora = OffsetDateTime.now();
        executarSeguro("agendas", () -> agendas(agora));
        executarSeguro("limiares", () -> limiares(agora));
        executarSeguro("retomadas", () -> retomadas(agora));
        executarSeguro("tempo limite", () -> tempoLimite(agora));
    }

    @Scheduled(cron = "${ces.automacao.expurgo-cron:0 40 3 * * *}")
    public void expurgarArquivos() {
        int removidos = arquivos.expurgar(OffsetDateTime.now().minusDays(30));
        if (removidos > 0) LOGGER.info("{} arquivos de execuções antigas de automação removidos.", removidos);
    }

    private void executarSeguro(String etapa, Runnable acao) {
        try {
            acao.run();
        } catch (RuntimeException e) {
            LOGGER.error("Tick das automações falhou em {}", etapa, e);
        }
    }

    private Map<String, Object> parametrosDoGatilho(Automacao a) {
        VersaoAutomacao versao = versoes.findByIdAutomacaoAndNumero(a.getId(), a.getNumeroVersao()).orElse(null);
        return versao == null ? null : DefinicaoAutomacao.ler(versao.getDefinicao()).gatilho().params();
    }

    void agendas(OffsetDateTime agora) {
        for (Automacao a : automacoes.buscarAtivasPorGatilho("trigger.schedule")) {
            try {
                Map<String, Object> params = parametrosDoGatilho(a);
                if (params == null) continue;
                OffsetDateTime slot = Recorrencia.ultimo(params, agora);
                if (slot == null) continue;
                if (a.getUltimoDisparo() != null && !slot.isAfter(a.getUltimoDisparo())) continue;
                if (a.getAtivadaEm() != null && slot.isBefore(a.getAtivadaEm())) continue;
                if (Duration.between(slot, agora).compareTo(Recorrencia.tolerancia(params)) > 0) {
                    // Atrasado demais (servidor parado): pula este horário, sem disparar fora de hora.
                    transacao.executeWithoutResult(s -> registrar(a.getId(), slot));
                    continue;
                }
                transacao.executeWithoutResult(s -> {
                    Automacao atual = automacoes.findById(a.getId()).orElseThrow();
                    Map<String, Object> carga = new HashMap<>();
                    carga.put("agendado_para", slot.toString());
                    fila.disparar(new ExecucaoAutomacoesService.Disparo(atual, ModoExecucao.AUTOMATICA,
                            "agenda:" + slot.truncatedTo(ChronoUnit.MINUTES), carga, null, null, null, null));
                    atual.registrarDisparo(slot);
                    automacoes.save(atual);
                });
            } catch (RuntimeException e) {
                LOGGER.error("Agenda da automação {} falhou", a.getId(), e);
            }
        }
    }

    private void registrar(UUID id, OffsetDateTime slot) {
        automacoes.findById(id).ifPresent(a -> {
            a.registrarDisparo(slot);
            automacoes.save(a);
        });
    }

    void limiares(OffsetDateTime agora) {
        List<Automacao> lista = automacoes.buscarAtivasPorPrefixoGatilho("threshold.%");
        if (lista.isEmpty()) return;
        Map<String, ProvedorMetricaGatilho> porChave = new HashMap<>();
        for (ProvedorMetricaGatilho p : provedores) for (String chave : p.chavesGatilho()) porChave.put(chave, p);
        Map<String, Long> medicoes = new HashMap<>();
        for (Automacao a : lista) {
            try {
                if (a.getProximaVerificacao() != null && a.getProximaVerificacao().isAfter(agora)) continue;
                Map<String, Object> params = parametrosDoGatilho(a);
                if (params == null) continue;
                String chave = a.getTipoGatilho().substring("threshold.".length());
                ProvedorMetricaGatilho provedor = porChave.get(chave);
                if (provedor == null) continue;
                Long valor = medicoes.computeIfAbsent(chave, provedor::medir);
                long limiar = (long) Math.max(1, Valores.dbl(params.getOrDefault("limiar", 1)));
                long intervalo = (long) Math.max(5, Valores.dbl(params.getOrDefault("verificar_minutos", 15)));
                long silencio = (long) Math.max(0, Valores.dbl(params.getOrDefault("silencio_minutos", 240)));
                transacao.executeWithoutResult(s -> {
                    Automacao atual = automacoes.findById(a.getId()).orElseThrow();
                    atual.agendarVerificacao(agora.plusMinutes(intervalo));
                    boolean emSilencio = atual.getUltimoDisparo() != null
                            && atual.getUltimoDisparo().plusMinutes(silencio).isAfter(agora);
                    if (valor != null && valor >= limiar && !emSilencio) {
                        Map<String, Object> carga = new HashMap<>();
                        carga.put("total", valor);
                        fila.disparar(new ExecucaoAutomacoesService.Disparo(atual, ModoExecucao.AUTOMATICA,
                                "limiar:" + agora.truncatedTo(ChronoUnit.MINUTES), carga, null, null, null, null));
                        atual.registrarDisparo(agora);
                    }
                    automacoes.save(atual);
                });
            } catch (RuntimeException e) {
                LOGGER.error("Limiar da automação {} falhou", a.getId(), e);
            }
        }
    }

    void retomadas(OffsetDateTime agora) {
        for (UUID id : execucoes.buscarParaRetomar(agora, agora.minusSeconds(30))) fila.submeter(id);
    }

    void tempoLimite(OffsetDateTime agora) {
        for (ExecucaoAutomacao e : execucoes.findByStatusIn(List.of(StatusExecucao.AGUARDANDO, StatusExecucao.NA_FILA))) {
            Automacao a = automacoes.findById(e.getIdAutomacao()).orElse(null);
            int horas = 168;
            VersaoAutomacao v = versoes.findById(e.getIdVersao()).orElse(null);
            if (v != null) horas = DefinicaoAutomacao.ler(v.getDefinicao()).configuracoes().limiteHoras();
            if (a != null && e.getCriadaEm().plusHours(horas).isBefore(agora)) {
                e.encerrarPorTempo(agora);
                execucoes.save(e);
            }
        }
    }
}
