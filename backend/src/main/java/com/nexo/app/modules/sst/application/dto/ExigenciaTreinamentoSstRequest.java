package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.ModalidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.ModoAplicabilidadeTreinamentoSst;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.PoliticaInstituicaoTreinamentoSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoFonteExigenciaTreinamentoSst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ExigenciaTreinamentoSstRequest(
        @NotNull UUID idTreinamento,
        @NotNull TipoCorpoGovernancaSst tipoCorpo,
        @NotNull ModoAplicabilidadeTreinamentoSst modoAplicabilidade,
        UUID idFuncao,
        PapelMembroGovernancaSst papelCorpo,
        UUID idBase,
        @Positive int cargaHorariaMinutos,
        @NotNull PoliticaInstituicaoTreinamentoSst politicaInstituicao,
        Set<String> instituicoesPermitidas,
        ModalidadeTreinamentoSst modalidade,
        @Positive Integer recorrenciaMeses,
        @PositiveOrZero int antecedenciaAlertaDias,
        @NotNull LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        @NotNull TipoFonteExigenciaTreinamentoSst tipoFonte,
        @Size(max = 60) String jurisdicao,
        @NotBlank @Size(max = 160) String identificadorFonte,
        @Size(max = 80) String edicaoFonte,
        @Size(max = 500) String urlFonte,
        boolean fonteConfirmada,
        @Size(max = 500) String observacoes
) {
}
