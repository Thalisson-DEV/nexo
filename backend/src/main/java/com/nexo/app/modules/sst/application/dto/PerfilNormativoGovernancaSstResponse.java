package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.EscopoPerfilNormativoSst;
import com.nexo.app.modules.sst.domain.PapelMembroGovernancaSst;
import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record PerfilNormativoGovernancaSstResponse(
        UUID id,
        Long versao,
        String codigo,
        Integer versaoPerfil,
        EscopoPerfilNormativoSst escopo,
        TipoCorpoGovernancaSst tipoCorpo,
        String jurisdicao,
        String orgaoEmissor,
        String atoNormativo,
        String edicao,
        LocalDate inicioVigencia,
        LocalDate fimVigencia,
        String urlOficial,
        Map<PapelMembroGovernancaSst, Integer> requisitosPapeis,
        boolean confirmado,
        OffsetDateTime dataConfirmacao,
        UUID idUsuarioConfirmacao,
        boolean ativo
) {
}
