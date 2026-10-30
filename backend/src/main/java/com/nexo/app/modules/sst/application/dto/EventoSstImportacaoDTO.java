package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.shared.application.dto.ImportacaoStatus;

/**
 * Uma linha do CSV de eventos. Os valores permanecem textuais durante a
 * conferência para que o usuário consiga corrigir a pré-visualização antes de
 * confirmar o lote.
 */
public record EventoSstImportacaoDTO(
        Integer linha,
        String tipo,
        String dataOcorrencia,
        String dataComunicacao,
        String atividade,
        String gestor,
        String baseOperacional,
        String equipe,
        String veiculo,
        String classificacao,
        String processo,
        String telemetria,
        String filmagem,
        String localOcorrencia,
        String latitude,
        String longitude,
        String descricao,
        String localLesao,
        String naturezaLesao,
        String agenteCausador,
        String diasPerdidos,
        String possuiCat,
        String tipoCat,
        String numeroCat,
        String dataEmissaoCat,
        String tecnicos,
        String envolvidos,
        ImportacaoStatus status,
        String mensagemErro
) {
    public EventoSstImportacaoDTO withStatus(ImportacaoStatus novoStatus, String novaMensagem) {
        return new EventoSstImportacaoDTO(
                linha, tipo, dataOcorrencia, dataComunicacao, atividade, gestor,
                baseOperacional, equipe, veiculo, classificacao, processo,
                telemetria, filmagem, localOcorrencia, latitude, longitude,
                descricao, localLesao, naturezaLesao, agenteCausador, diasPerdidos,
                possuiCat, tipoCat, numeroCat, dataEmissaoCat, tecnicos, envolvidos,
                novoStatus, novaMensagem);
    }
}
