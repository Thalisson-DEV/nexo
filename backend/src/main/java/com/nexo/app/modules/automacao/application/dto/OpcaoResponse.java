package com.nexo.app.modules.automacao.application.dto;

/** Opção para os seletores do designer (usuários, colaboradores, perfis, bases…). */
public record OpcaoResponse(
        String valor,
        String rotulo,
        String detalhe
) {
}
