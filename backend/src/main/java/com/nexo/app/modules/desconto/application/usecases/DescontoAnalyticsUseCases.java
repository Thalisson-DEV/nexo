package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.DescontoAnalyticsApi;
import com.nexo.app.modules.desconto.domain.StatusDesconto;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DescontoAnalyticsUseCases implements DescontoAnalyticsApi {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_LIMIT = 100;

    private final DescontoRepository repository;

    @Override
    public RankingTaxaSucesso rankingTaxaSucessoPorTipoEquipamento(
            OffsetDateTime inicioInclusivo,
            OffsetDateTime fimExclusivo,
            int amostraMinima,
            int limite) {
        validarParametros(inicioInclusivo, fimExclusivo, amostraMinima, limite);

        Map<UUID, Acumulador> porTipo = new LinkedHashMap<>();
        repository.listarResultadosFinaisDistintosPorTipo(inicioInclusivo, fimExclusivo)
                .forEach(row -> porTipo.computeIfAbsent(
                                row.getIdTipoEquipamento(),
                                id -> new Acumulador(id, row.getTipoEquipamento()))
                        .adicionar(row.getStatus()));

        List<TaxaSucessoTipo> tipos = porTipo.values().stream()
                .filter(item -> item.total() >= amostraMinima)
                .map(Acumulador::resultado)
                .sorted(Comparator.comparing(TaxaSucessoTipo::taxaSucessoPercentual).reversed()
                        .thenComparing(TaxaSucessoTipo::resultadosFinais, Comparator.reverseOrder())
                        .thenComparing(TaxaSucessoTipo::tipoEquipamento))
                .limit(limite)
                .toList();

        long totalResultados = porTipo.values().stream().mapToLong(Acumulador::total).sum();
        return new RankingTaxaSucesso(
                "desconto.taxa_sucesso.tipo_equipamento",
                "1.0.0",
                inicioInclusivo,
                fimExclusivo,
                amostraMinima,
                tipos,
                totalResultados,
                OffsetDateTime.now(ZONE_ID));
    }

    private void validarParametros(OffsetDateTime inicio, OffsetDateTime fim, int amostraMinima, int limite) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new IllegalArgumentException("Periodo invalido: inicio deve ser anterior ao fim");
        }
        if (amostraMinima < 1 || amostraMinima > 10_000) {
            throw new IllegalArgumentException("Amostra minima deve estar entre 1 e 10000");
        }
        if (limite < 1 || limite > MAX_LIMIT) {
            throw new IllegalArgumentException("Limite deve estar entre 1 e " + MAX_LIMIT);
        }
    }

    private static final class Acumulador {
        private final UUID id;
        private final String nome;
        private long baixados;
        private long rejeitados;

        private Acumulador(UUID id, String nome) {
            this.id = id;
            this.nome = nome;
        }

        private void adicionar(StatusDesconto status) {
            if (status == StatusDesconto.BAIXADO) {
                baixados++;
            } else if (status == StatusDesconto.REJEITADO) {
                rejeitados++;
            }
        }

        private long total() {
            return baixados + rejeitados;
        }

        private TaxaSucessoTipo resultado() {
            BigDecimal percentual = BigDecimal.valueOf(baixados)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total()), 2, RoundingMode.HALF_UP);
            return new TaxaSucessoTipo(id, nome, baixados, rejeitados, total(), percentual);
        }
    }
}
