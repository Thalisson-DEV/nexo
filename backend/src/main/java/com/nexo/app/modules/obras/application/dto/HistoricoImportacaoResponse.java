package com.nexo.app.modules.obras.application.dto;

import com.nexo.app.modules.obras.domain.ImportacaoObra;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Registro do histórico de importações de planilhas de Obras (kits, carteira, orçamento previsto).
 */
public record HistoricoImportacaoResponse(
        UUID id,
        ImportacaoObra.Tipo tipo,
        UUID idContrato,
        String nomeArquivo,
        ImportacaoObra.Status status,
        int totalLinhas,
        int inseridos,
        int atualizados,
        int erros,
        List<ImportacaoObra.ErroLinha> detalheErros,
        String mensagem,
        String usuarioCriacao,
        OffsetDateTime dataCriacao,
        OffsetDateTime dataConclusao
) {
}
