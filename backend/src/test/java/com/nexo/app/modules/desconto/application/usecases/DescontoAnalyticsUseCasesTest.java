package com.nexo.app.modules.desconto.application.usecases;

import com.nexo.app.modules.desconto.domain.StatusDesconto;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DescontoAnalyticsUseCasesTest {

    @Mock DescontoRepository repository;

    @Test
    void taxaUsaSomenteBaixadoMaisRejeitadoEAmostraMinima() {
        UUID capacete = UUID.randomUUID();
        UUID luva = UUID.randomUUID();
        List<DescontoRepository.ResultadoFinalPorTipoProjection> rows = new ArrayList<>();
        for (int i = 0; i < 8; i++) rows.add(new Row(capacete, "Capacete", StatusDesconto.BAIXADO));
        for (int i = 0; i < 2; i++) rows.add(new Row(capacete, "Capacete", StatusDesconto.REJEITADO));
        for (int i = 0; i < 5; i++) rows.add(new Row(luva, "Luva", StatusDesconto.BAIXADO));
        when(repository.listarResultadosFinaisDistintosPorTipo(any(), any())).thenReturn(rows);

        var result = new DescontoAnalyticsUseCases(repository).rankingTaxaSucessoPorTipoEquipamento(
                OffsetDateTime.parse("2026-01-01T00:00:00-03:00"),
                OffsetDateTime.parse("2027-01-01T00:00:00-03:00"), 10, 10);

        assertThat(result.tipos()).singleElement().satisfies(item -> {
            assertThat(item.tipoEquipamento()).isEqualTo("Capacete");
            assertThat(item.resultadosFinais()).isEqualTo(10);
            assertThat(item.taxaSucessoPercentual()).isEqualByComparingTo(new BigDecimal("80.00"));
        });
    }

    private record Row(UUID idTipoEquipamento, String tipoEquipamento, StatusDesconto status)
            implements DescontoRepository.ResultadoFinalPorTipoProjection {
        @Override public UUID getIdTipoEquipamento() { return idTipoEquipamento; }
        @Override public String getTipoEquipamento() { return tipoEquipamento; }
        @Override public UUID getIdDesconto() { return UUID.randomUUID(); }
        @Override public StatusDesconto getStatus() { return status; }
    }
}
