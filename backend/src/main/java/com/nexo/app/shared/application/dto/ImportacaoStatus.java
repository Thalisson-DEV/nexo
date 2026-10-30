package com.nexo.app.shared.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImportacaoStatus {
    AGUARDANDO_PROCESSAMENTO("Aguardando processamento"),
    COM_ERRO("Com erro"),
    EM_PROCESSAMENTO("Em processamento"),
    CONCLUIDO("Concluído");

    private final String descricao;
}
