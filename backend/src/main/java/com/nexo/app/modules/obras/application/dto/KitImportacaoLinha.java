package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

/**
 * Linha da planilha de kits: um item de um kit. Campos em texto para a correção na pré-visualização.
 */
public record KitImportacaoLinha(
        Integer linha,
        String codigoKit,
        String descricaoKit,
        String grupo,
        String tensoes,
        String tipoItem,
        String codigoItem,
        String operacao,
        String quantidade,
        String unidade,
        ImportacaoStatus status,
        String mensagemErro
) {
    public KitImportacaoLinha withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new KitImportacaoLinha(linha, codigoKit, descricaoKit, grupo, tensoes, tipoItem, codigoItem, operacao,
                quantidade, unidade, novoStatus, novaMensagem);
    }
}
