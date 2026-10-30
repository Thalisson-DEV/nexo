package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.MotivoEncerramentoCorpoSst;
import com.nexo.app.modules.sst.domain.RegimeComposicaoSst;
import com.nexo.app.modules.sst.domain.StatusCorpoGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CorpoGovernancaSstResponse(
        UUID id,
        Long versao,
        TipoCorpoGovernancaSst tipo,
        RegimeComposicaoSst regimeComposicao,
        StatusCorpoGovernancaSst status,
        String nome,
        UUID idBaseOperacional,
        String baseNome,
        String unidadeGovernanca,
        String uf,
        LocalDate inicioPeriodo,
        LocalDate fimPeriodo,
        Integer grauRisco,
        UUID idPerfilNormativo,
        boolean hipoteseRepresentanteNomeadoConfirmada,
        boolean conformidadeNormativaValidada,
        String codigoAvaliacaoNormativa,
        String perfilCodigoSnapshot,
        String perfilEdicaoSnapshot,
        String perfilUrlSnapshot,
        int quantidadeMembrosAtivos,
        OffsetDateTime dataAtivacao,
        OffsetDateTime dataEncerramento,
        LocalDate dataEfeitoEncerramento,
        MotivoEncerramentoCorpoSst motivoEncerramento,
        boolean ativo
) {
}
