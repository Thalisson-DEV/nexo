package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ModalidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.ModoAplicabilidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.PoliticaInstituicaoTreinamentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoFonteExigenciaTreinamentoSst;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record ExigenciaTreinamentoSstResponse(
        UUID id,
        UUID idGrupoVersao,
        int revisaoExigencia,
        UUID idExigenciaAnterior,
        boolean atual,
        UUID idTreinamento,
        String treinamentoCodigo,
        String treinamentoNome,
        TipoCorpoGovernancaSst tipoCorpo,
        ModoAplicabilidadeTreinamentoSst modoAplicabilidade,
        UUID idFuncao,
        PapelMembroGovernancaSst papelCorpo,
        UUID idBase,
        int cargaHorariaMinutos,
        PoliticaInstituicaoTreinamentoSst politicaInstituicao,
        Set<String> instituicoesPermitidas,
        ModalidadeTreinamentoSst modalidade,
        Integer recorrenciaMeses,
        int antecedenciaAlertaDias,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        TipoFonteExigenciaTreinamentoSst tipoFonte,
        String jurisdicao,
        String identificadorFonte,
        String edicaoFonte,
        String urlFonte,
        boolean fonteConfirmada,
        OffsetDateTime dataConfirmacaoFonte,
        String observacoes,
        boolean ativa,
        Long versao
) {
}
