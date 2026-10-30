package com.nexo.app.modules.financeiro.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O que se pode fazer com <b>esta</b> solicitação, no estado em que ela se
 * encontra.
 *
 * <p>Resolver isso no servidor evita que a tela reimplemente a máquina de estados
 * — e evita a experiência de oferecer um botão que só falha depois do clique. As
 * permissões continuam sendo cobradas nos endpoints; isto é a habilitação da
 * interface, não a autorização.</p>
 */
@Schema(description = "Ações habilitadas nesta solicitação")
public record AcoesDisponiveisResponse(

        @Schema(description = "Pode alterar o conteúdo preenchido e mexer nos anexos")
        boolean podeEditar,

        @Schema(description = "Pode enviar o rascunho para aprovação")
        boolean podeSubmeter,

        @Schema(description = "Aguarda decisão e portanto pode ser aprovada ou rejeitada")
        boolean podeDecidir,

        @Schema(description = "Pode cancelar a solicitação")
        boolean podeCancelar,

        @Schema(description = "Foi aprovada ou rejeitada e a decisão pode ser desfeita")
        boolean podeDesfazerDecisao
) {}
