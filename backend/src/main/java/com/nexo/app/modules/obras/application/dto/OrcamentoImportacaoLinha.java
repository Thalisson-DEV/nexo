package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

/**
 * Linha da planilha de serviços/orçamento previstos: um serviço de uma obra.
 * {@code mensagemErro} também carrega avisos (ex.: código sem Ação no contrato) em linhas válidas.
 */
public record OrcamentoImportacaoLinha(
        Integer linha,
        String numeroObra,
        String codigoServico,
        String descricao,
        String unidade,
        String quantidade,
        String valorUnitario,
        String valorTotal,
        String valorMaterialPrevisto,
        String valorServicoPrevisto,
        ImportacaoStatus status,
        String mensagemErro
) {
    public OrcamentoImportacaoLinha withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new OrcamentoImportacaoLinha(linha, numeroObra, codigoServico, descricao, unidade, quantidade,
                valorUnitario, valorTotal, valorMaterialPrevisto, valorServicoPrevisto, novoStatus, novaMensagem);
    }
}
