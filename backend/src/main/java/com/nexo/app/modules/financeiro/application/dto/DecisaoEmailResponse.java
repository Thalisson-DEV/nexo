package com.nexo.app.modules.financeiro.application.dto;

import com.nexo.app.modules.financeiro.domain.DecisaoPorEmail;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * O que a página do link mostra: a solicitação e se a decisão ainda cabe.
 *
 * <p>Só existe para quem tem o link, e o link só foi enviado ao aprovador; ainda
 * assim, nada aqui vai além do que o próprio e-mail já mostrava.</p>
 */
@Schema(description = "Situação de um link de decisão enviado por e-mail")
public record DecisaoEmailResponse(

        @Schema(description = "DISPONIVEL, DECIDIDA_POR_ESTE_LINK, JA_DECIDIDA, EXPIRADO ou SEM_PERMISSAO")
        Situacao situacao,

        @Schema(description = "Texto para quem está na página, já pronto")
        String mensagem,

        @Schema(description = "Ex.: Compra nº 8")
        String titulo,

        String situacaoSolicitacao,

        BigDecimal valorSolicitado,

        @Schema(description = "Nome de quem recebeu o link; a decisão fica em nome dele")
        String decisor,

        OffsetDateTime expiraEm,

        @Schema(description = "A decisão tomada por este link, quando já usado")
        DecisaoPorEmail.Acao acaoTomada,

        List<CampoFicha> ficha
) {

    public enum Situacao {
        DISPONIVEL,
        DECIDIDA_POR_ESTE_LINK,
        JA_DECIDIDA,
        EXPIRADO,
        SEM_PERMISSAO
    }

    public record CampoFicha(String rotulo, String valor) {
    }
}
