package com.nexo.app.modules.rh.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

/**
 * DTO para representar uma linha de importação de Processo de Negócio.
 */
public record ProcessoNegocioImportacaoDTO(
    Integer linha,
    String nome,
    ImportacaoStatus status,
    String mensagemErro
) {
    public ProcessoNegocioImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new ProcessoNegocioImportacaoDTO(
            linha, nome != null ? nome.toUpperCase() : null, novoStatus, novaMensagem
        );
    }
}
