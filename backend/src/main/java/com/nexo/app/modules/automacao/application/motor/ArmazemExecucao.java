package com.nexo.app.modules.automacao.application.motor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Onde o interpretador grava e relê as etapas de uma execução. */
public interface ArmazemExecucao {

    Map<String, RegistroEtapa> carregar();

    void salvar(RegistroEtapa registro);

    boolean cancelada();

    void sinalVital();

    UUID abrirAprovacao(RegistroEtapa registro, PedidoAprovacao pedido);

    /** Resultado da aprovação decidida; nulo enquanto pendente. */
    Map<String, Object> resultadoAprovacao(UUID aprovacaoId);

    void encerrarAprovacao(UUID aprovacaoId, String status);

    record PedidoAprovacao(String noId, String iteracao, List<UUID> aprovadores, List<String> emails, String titulo,
                           String detalhes, List<String> opcoes, OffsetDateTime expiraEm) {
    }
}
