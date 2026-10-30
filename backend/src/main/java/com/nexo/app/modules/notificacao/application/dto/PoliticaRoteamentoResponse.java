package com.nexo.app.modules.notificacao.application.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.nexo.app.modules.notificacao.domain.CondicaoRotaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.domain.OperadorCondicao;
import com.nexo.app.modules.notificacao.domain.PoliticaRoteamentoNotificacao;
import com.nexo.app.modules.notificacao.domain.RotaRoteamentoNotificacao;

public record PoliticaRoteamentoResponse(
        UUID id,
        String chaveGatilho,
        String nome,
        String descricao,
        boolean ativo,
        List<RotaResponse> rotas,
        OffsetDateTime dataCriacao
) {
    public static PoliticaRoteamentoResponse de(PoliticaRoteamentoNotificacao politica) {
        return new PoliticaRoteamentoResponse(
                politica.getId(),
                politica.getChaveGatilho(),
                politica.getNome(),
                politica.getDescricao(),
                politica.isAtivo(),
                politica.rotasAtivasOrdenadas().stream().map(RotaResponse::de).toList(),
                politica.getDataCriacao());
    }

    public record RotaResponse(
            UUID id,
            int ordem,
            boolean padrao,
            UUID usuarioId,
            List<CondicaoResponse> condicoes
    ) {
        static RotaResponse de(RotaRoteamentoNotificacao rota) {
            return new RotaResponse(
                    rota.getId(), rota.getOrdem(), rota.isPadrao(), rota.getUsuarioId(),
                    rota.condicoesAtivas().stream().map(CondicaoResponse::de).toList());
        }
    }

    public record CondicaoResponse(
            UUID id,
            String campo,
            OperadorCondicao operador,
            String operadorRotulo,
            String valor
    ) {
        static CondicaoResponse de(CondicaoRotaRoteamentoNotificacao condicao) {
            return new CondicaoResponse(
                    condicao.getId(), condicao.getCampo(), condicao.getOperador(),
                    condicao.getOperador().getRotulo(), condicao.getValor());
        }
    }
}
