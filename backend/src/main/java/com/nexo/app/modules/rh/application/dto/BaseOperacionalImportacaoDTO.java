package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

import java.util.Locale;

public record BaseOperacionalImportacaoDTO(
    Integer linha,
    String nome,
    String nomeCentroFaturamento,
    String uf,
    String endereco,
    ImportacaoStatus status,
    String mensagemErro
) {
    public BaseOperacionalImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new BaseOperacionalImportacaoDTO(
            linha, maiusculo(nome), maiusculo(nomeCentroFaturamento),
            maiusculo(uf), endereco, novoStatus, novaMensagem
        );
    }

    private static String maiusculo(String valor) {
        return valor == null ? null : valor.toUpperCase(Locale.ROOT);
    }
}
