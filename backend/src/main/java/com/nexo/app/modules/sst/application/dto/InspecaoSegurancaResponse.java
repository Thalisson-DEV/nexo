package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.EquipeResponse;
import com.nexo.app.modules.rh.ProcessoNegocioResponse;
import com.nexo.app.modules.rh.VeiculoResponse;
import com.nexo.app.modules.sst.domain.StatusInspecao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resposta detalhada de uma inspeção de segurança lançada. A {@code equipe}, quando
 * presente, vem hidratada com todos os seus integrantes — atendendo à entrega
 * automática dos membros ao client.
 */
public record InspecaoSegurancaResponse(
        UUID id,
        FormularioInspecaoResumoResponse formulario,
        TipoInspecaoResponse tipoInspecao,
        ProcessoNegocioResponse processo,
        ProcessoNegocioResponse processoInspetor,
        UUID idInspetor,
        String nomeInspetor,
        VeiculoResponse veiculo,
        EquipeResponse equipe,
        BaseOperacionalResponse baseOperacional,
        OffsetDateTime dataRealizacao,
        StatusInspecao status,
        String observacoes,
        BigDecimal latitude,
        BigDecimal longitude,
        List<ColaboradorResponse> colaboradoresInspecionados,
        List<RespostaInspecaoResponse> respostas,
        List<DesvioSegurancaResponse> desvios,
        List<EvidenciaInspecaoResponse> evidencias
) {}
