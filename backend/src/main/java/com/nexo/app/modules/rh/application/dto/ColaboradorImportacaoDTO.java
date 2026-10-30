package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

public record ColaboradorImportacaoDTO(
    Integer linha,
    String primeiroNome,
    String ultimoNome,
    String nomeCompleto,
    String cpf,
    String nomeProcesso,
    String nomeBase,
    String nomeFuncao,
    String dataContratacao,
    ImportacaoStatus status,
    String mensagemErro
) {
    public ColaboradorImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new ColaboradorImportacaoDTO(
            linha, primeiroNome, ultimoNome, nomeCompleto, cpf,
            nomeProcesso, nomeBase, nomeFuncao, dataContratacao,
            novoStatus, novaMensagem
        );
    }
}
