package com.nexo.app.modules.automacao.application.usecases;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.nexo.app.modules.automacao.domain.Automacao;
import com.nexo.app.modules.automacao.domain.ModoExecucao;
import com.nexo.app.modules.automacao.domain.expressao.Valores;
import com.nexo.app.modules.automacao.infrastructure.persistence.AutomacaoRepository;
import com.nexo.app.shared.domain.events.EventoNotificavel;

/**
 * Ponte entre os eventos de domínio e as automações. Um ouvinte só, sobre a interface,
 * atende todos os gatilhos por ocorrência — os de hoje e os que vierem.
 *
 * <p>A entrega passa pela tabela {@code event_publication} do Spring Modulith, depois do
 * commit de quem publicou: uma automação que falha nunca desfaz o desconto validado, e
 * a reentrega é segura porque a chave do disparo (gatilho + registro + conteúdo) é
 * idempotente.</p>
 */
@Component
public class OuvinteEventosAutomacao {

    private static final Logger LOGGER = LoggerFactory.getLogger(OuvinteEventosAutomacao.class);

    private final AutomacaoRepository automacoes;
    private final ExecucaoAutomacoesService fila;

    public OuvinteEventosAutomacao(AutomacaoRepository automacoes, ExecucaoAutomacoesService fila) {
        this.automacoes = automacoes;
        this.fila = fila;
    }

    @ApplicationModuleListener
    public void on(EventoNotificavel evento) {
        List<Automacao> interessadas = automacoes.buscarAtivasPorEvento(evento.chaveGatilho());
        if (interessadas.isEmpty()) return;
        Map<String, Object> carga = new HashMap<>();
        carga.put("contexto", evento.contexto() == null ? Map.of() : evento.contexto());
        Map<String, Object> papeis = new HashMap<>();
        if (evento.papeis() != null) {
            evento.papeis().forEach((papel, ids) -> papeis.put(papel, ids == null ? List.of() : List.copyOf(ids)));
        }
        carga.put("papeis", papeis);
        carga.put("id_referencia", evento.idReferencia() == null ? null : evento.idReferencia().toString());
        String chave = "evento:" + evento.chaveGatilho() + ":" + resumo(evento);
        for (Automacao automacao : interessadas) {
            try {
                fila.disparar(new ExecucaoAutomacoesService.Disparo(automacao, ModoExecucao.AUTOMATICA, chave, carga,
                        evento.idReferencia(), null, null, null));
            } catch (RuntimeException e) {
                LOGGER.error("Automação {} não pôde reagir ao evento {}", automacao.getId(), evento.chaveGatilho(), e);
            }
        }
    }

    /** Hash estável do conteúdo: a reentrega do mesmo evento dá a mesma chave. */
    private static String resumo(EventoNotificavel evento) {
        Map<String, Object> ordenado = new TreeMap<>();
        if (evento.contexto() != null) evento.contexto().forEach((k, v) -> ordenado.put(k, Valores.texto(v)));
        Map<String, Object> papeis = new TreeMap<>();
        if (evento.papeis() != null) {
            evento.papeis().forEach((k, v) -> papeis.put(k, v == null ? "" : new java.util.TreeSet<>(v.stream().map(UUID::toString).toList()).toString()));
        }
        String base = evento.idReferencia() + "|" + ordenado + "|" + papeis;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(base.hashCode());
        }
    }
}
