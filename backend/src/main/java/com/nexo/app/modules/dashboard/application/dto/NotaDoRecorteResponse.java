package com.nexo.app.modules.dashboard.application.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Uma nota concluída que entra na soma dos dashboards de execução: quem lançou, quando,
 * de que tipo, quanto valeu e o que foi executado nela.
 */
public record NotaDoRecorteResponse(
        UUID id,
        String referenciaNota,
        UUID idTurno,
        UUID idEquipe,
        String codigoEquipe,
        String baseOperacional,
        String processo,
        /* Nulos nas notas lançadas antes do cadastro de tipos de serviço. */
        UUID idTipoServico,
        String codigoTipoServico,
        String tipoServico,
        OffsetDateTime dataLancamento,
        BigDecimal valor,
        /* "Código · nome" de cada ação executada. */
        List<String> acoes,
        int materiais,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
