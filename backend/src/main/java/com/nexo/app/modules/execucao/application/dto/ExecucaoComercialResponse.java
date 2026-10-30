package com.nexo.app.modules.execucao.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexo.app.modules.execucao.domain.StatusExecucao;
import com.nexo.app.modules.execucao.domain.TipoNota;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.MunicipioResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ExecucaoComercialResponse(
        UUID id,
        String referenciaNota,
        UUID idTurno,
        /* Nulo nas execuções lançadas antes do cadastro de tipos de serviço. */
        TipoServicoResponse tipoServico,
        EquipeResponse equipe,
        MunicipioResponse municipio,
        BigDecimal latitude,
        BigDecimal longitude,
        OffsetDateTime dataLancamento,
        StatusExecucao statusExecucao,
        BigDecimal valorTotalFaturado,
        UUID idUsuarioAlteracao,
        OffsetDateTime dataAlteracao,
        List<ExecucaoAcaoResponse> acoes,
        List<ExecucaoMaterialResponse> materiais,
        TipoNota tipoNota,
        /* Número gerado pelo app para a nota avulsa; nulo nos demais tipos. */
        String referenciaProvisoria,
        /* Quando o número oficial da avulsa foi informado; nulo enquanto ela espera. */
        OffsetDateTime dataNumeroOficial,
        /* Quantas vezes a nota foi alterada depois de sincronizada; 0 = como foi lançada. */
        int totalAlteracoes
) {

    /** A nota já não é como foi lançada: serviços ou materiais corrigidos depois. */
    @JsonProperty("alterada")
    public boolean alterada() {
        return totalAlteracoes > 0;
    }

    /** Nota avulsa que ainda está com o número provisório. */
    @JsonProperty("aguardandoNumeroOficial")
    public boolean aguardandoNumeroOficial() {
        return tipoNota == TipoNota.AVULSA && dataNumeroOficial == null;
    }


    /*
     * Aliases de transição para aparelhos com versões antigas do app de campo, que
     * ainda leem os nomes de antes da data de lançamento única. Remover quando todos
     * os aparelhos estiverem na versão que lê dataLancamento/latitude/longitude.
     */

    @Deprecated
    @JsonProperty("dataInicio")
    @Schema(deprecated = true, description = "Use dataLancamento.")
    public OffsetDateTime dataInicioLegado() {
        return dataLancamento;
    }

    @Deprecated
    @JsonProperty("latitudeInicio")
    @Schema(deprecated = true, description = "Use latitude.")
    public BigDecimal latitudeInicioLegado() {
        return latitude;
    }

    @Deprecated
    @JsonProperty("longitudeInicio")
    @Schema(deprecated = true, description = "Use longitude.")
    public BigDecimal longitudeInicioLegado() {
        return longitude;
    }
}
