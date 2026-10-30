package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.GravidadeDesvio;
import com.nexo.app.modules.sst.domain.StatusExecucaoChecklistGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoResposta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExecucaoChecklistGovernancaResponse(
        UUID id,
        UUID idModelo,
        String modelo,
        UUID idFormulario,
        UUID idCorpo,
        UUID idBaseOperacional,
        UUID idExecutor,
        String nomeExecutor,
        OffsetDateTime dataExecucao,
        StatusExecucaoChecklistGovernancaSst status,
        String observacoes,
        OffsetDateTime dataConclusao,
        List<RespostaItem> respostas,
        List<ConstatacaoItem> constatacoes,
        Long versao,
        OffsetDateTime dataCriacao) {

    public record RespostaItem(
            UUID id,
            UUID idPergunta,
            String pergunta,
            TipoResposta tipoResposta,
            boolean obrigatoria,
            String valorTexto,
            BigDecimal valorNumero,
            LocalDate valorData,
            List<OpcaoItem> opcoes) {
    }

    public record OpcaoItem(UUID idOpcao, String texto, boolean naoConforme, GravidadeDesvio gravidade) {
    }

    public record ConstatacaoItem(
            UUID id,
            String pergunta,
            String opcao,
            GravidadeDesvio gravidade,
            String descricao,
            UUID idAcao) {
    }
}
