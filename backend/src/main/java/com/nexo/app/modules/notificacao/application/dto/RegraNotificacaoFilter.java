package com.nexo.app.modules.notificacao.application.dto;

import com.nexo.app.modules.notificacao.domain.CategoriaNotificacao;

public record RegraNotificacaoFilter(
        String buscaGlobal,
        String chaveGatilho,
        CategoriaNotificacao categoria,
        Boolean sistema,
        Boolean ativo
) {
}
