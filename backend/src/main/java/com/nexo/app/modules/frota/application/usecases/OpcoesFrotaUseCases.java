package com.nexo.app.modules.frota.application.usecases;

import com.nexo.app.modules.frota.application.dto.OpcaoFrota;
import com.nexo.app.modules.frota.application.dto.OpcoesFrotaResponse;
import com.nexo.app.modules.frota.domain.CategoriaCnh;
import com.nexo.app.modules.frota.domain.Combustivel;
import com.nexo.app.modules.frota.domain.OrigemLeituraOdometro;
import com.nexo.app.modules.frota.domain.ProvedorTelemetria;
import com.nexo.app.modules.frota.domain.RegimeVeiculo;
import com.nexo.app.modules.frota.domain.SituacaoOperacional;
import com.nexo.app.modules.frota.domain.TipoDocumentoFrota;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;

/** Listas fechadas da frota com o rótulo de exibição, para a tela não duplicar textos. */
@Service
public class OpcoesFrotaUseCases {

    private static final Map<OrigemLeituraOdometro, String> ORIGENS = Map.of(
            OrigemLeituraOdometro.MANUAL, "Lançamento manual",
            OrigemLeituraOdometro.CHECKLIST, "Checklist",
            OrigemLeituraOdometro.ABASTECIMENTO, "Abastecimento",
            OrigemLeituraOdometro.MANUTENCAO, "Manutenção",
            OrigemLeituraOdometro.TELEMETRIA, "Telemetria",
            OrigemLeituraOdometro.IMPORTACAO, "Importação");

    public OpcoesFrotaResponse listar() {
        return new OpcoesFrotaResponse(
                Arrays.stream(SituacaoOperacional.values()).map(v -> new OpcaoFrota(v.name(), v.getRotulo())).toList(),
                Arrays.stream(RegimeVeiculo.values()).map(v -> new OpcaoFrota(v.name(), v.getRotulo())).toList(),
                Arrays.stream(Combustivel.values()).map(v -> new OpcaoFrota(v.name(), v.getRotulo())).toList(),
                Arrays.stream(ProvedorTelemetria.values()).map(v -> new OpcaoFrota(v.name(), v.getRotulo())).toList(),
                Arrays.stream(CategoriaCnh.values()).map(v -> new OpcaoFrota(v.name(), v.name())).toList(),
                Arrays.stream(TipoDocumentoFrota.values()).map(v -> new OpcaoFrota(v.name(), v.getRotulo())).toList(),
                Arrays.stream(OrigemLeituraOdometro.values()).map(v -> new OpcaoFrota(v.name(), ORIGENS.get(v))).toList());
    }
}
