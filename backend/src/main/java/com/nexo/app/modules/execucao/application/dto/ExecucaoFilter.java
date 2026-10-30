package com.nexo.app.modules.execucao.application.dto;

import com.nexo.app.modules.execucao.domain.StatusExecucao;
import com.nexo.app.modules.execucao.domain.TipoNota;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtro dinâmico para listagem de execuções comerciais.
 */
public record ExecucaoFilter(
    String referenciaNota,
    String referencia,
    UUID idEquipe,
    UUID idTurno,
    UUID idMunicipio,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    OffsetDateTime dataLancamentoDe,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    OffsetDateTime dataLancamentoAte,

    Boolean apenasHoje,
    StatusExecucao status,
    UUID idAcao,
    UUID idMaterial,
    OperacaoMaterial operacaoMaterial,
    String numSerieMaterial,

    @Parameter(description = "Tipo de serviço da nota")
    UUID idTipoServico,

    @Parameter(description = "Apenas execuções sem tipo de serviço informado")
    Boolean semTipoServico,

    @Parameter(description = "Busca rápida global")
    String buscaGlobal,

    @Parameter(description = "Tipo do lançamento: COMUM, AVULSA ou OCORRENCIA")
    TipoNota tipoNota,

    @Parameter(description = "Apenas notas avulsas que ainda esperam o número oficial")
    Boolean aguardandoNumeroOficial,

    @Parameter(description = "Apenas notas alteradas depois de sincronizadas")
    Boolean alterada
) {
}
