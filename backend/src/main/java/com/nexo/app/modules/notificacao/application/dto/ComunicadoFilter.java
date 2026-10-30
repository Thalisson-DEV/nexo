package com.nexo.app.modules.notificacao.application.dto;

import com.nexo.app.modules.notificacao.domain.StatusComunicado;
import com.nexo.app.modules.notificacao.domain.TipoComunicado;

/**
 * Filtros da tela de administração do mural.
 */
public record ComunicadoFilter(
        String buscaGlobal,
        TipoComunicado tipo,
        StatusComunicado status,
        Boolean exigeConfirmacao,
        Boolean ativo
) {
}
