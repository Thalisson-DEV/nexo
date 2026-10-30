package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Memória de um aviso de vencimento já emitido.
 *
 * <p>Único por alvo, faixa e data de vencimento: o job diário pode rodar quantas vezes
 * for sem repetir o aviso, e um documento renovado (nova data) volta a ser avisado.</p>
 */
@Entity
@Table(name = "frota_alertas_vencimento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertaVencimento extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_alvo", nullable = false, length = 20, updatable = false)
    private TipoAlvoAlerta tipoAlvo;

    @Column(name = "id_alvo", nullable = false, updatable = false)
    private UUID idAlvo;

    @Column(name = "dias_antecedencia", nullable = false, updatable = false)
    private Integer diasAntecedencia;

    @Column(name = "data_vencimento", nullable = false, updatable = false)
    private LocalDate dataVencimento;

    @Column(name = "data_avaliacao", nullable = false, updatable = false)
    private LocalDate dataAvaliacao;

    public AlertaVencimento(TipoAlvoAlerta tipoAlvo, UUID idAlvo, int diasAntecedencia,
                            LocalDate dataVencimento, LocalDate dataAvaliacao) {
        this.tipoAlvo = Objects.requireNonNull(tipoAlvo);
        this.idAlvo = Objects.requireNonNull(idAlvo);
        this.diasAntecedencia = diasAntecedencia;
        this.dataVencimento = Objects.requireNonNull(dataVencimento);
        this.dataAvaliacao = Objects.requireNonNull(dataAvaliacao);
    }

    /**
     * Menor faixa já alcançada no dia avaliado, ou nulo se o vencimento ainda está
     * fora de todas. Ex.: faixas 30/15/7/0 e vencimento em 10 dias → 15.
     */
    public static Integer faixaAlcancada(java.util.List<Integer> faixasDecrescentes, LocalDate vencimento, LocalDate hoje) {
        Integer alcancada = null;
        for (Integer faixa : faixasDecrescentes) {
            if (!vencimento.minusDays(faixa).isAfter(hoje)) {
                alcancada = faixa;
            }
        }
        return alcancada;
    }
}
