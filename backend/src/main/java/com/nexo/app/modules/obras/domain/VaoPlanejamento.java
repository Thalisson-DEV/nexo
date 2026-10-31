package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Trecho entre dois pontos da obra, inclusive em páginas diferentes. A metragem é a base dos kits do vão.
 */
@Entity
@Table(name = "vaos_planejamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VaoPlanejamento extends BaseEntity {

    @Column(name = "id_obra", nullable = false)
    private UUID idObra;

    @Column(name = "id_ponto_a", nullable = false)
    private UUID idPontoA;

    @Column(name = "id_ponto_b", nullable = false)
    private UUID idPontoB;

    @Column(name = "metragem", precision = 10, scale = 2)
    private BigDecimal metragem;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemPlanejamento origem;

    public VaoPlanejamento(UUID idObra, UUID idPontoA, UUID idPontoB, BigDecimal metragem, OrigemPlanejamento origem) {
        if (idObra == null || idPontoA == null || idPontoB == null) {
            throw new InvarianteVioladaException("O vão precisa da obra e de dois pontos.");
        }
        if (idPontoA.equals(idPontoB)) {
            throw new InvarianteVioladaException("Um vão não pode ligar um ponto a ele mesmo.");
        }
        this.idObra = idObra;
        this.idPontoA = idPontoA;
        this.idPontoB = idPontoB;
        this.origem = origem != null ? origem : OrigemPlanejamento.MANUAL;
        informarMetragem(metragem);
    }

    public void informarMetragem(BigDecimal metragem) {
        if (metragem != null && metragem.signum() <= 0) {
            throw new InvarianteVioladaException("A metragem do vão deve ser maior que zero.");
        }
        this.metragem = metragem;
    }

    public boolean liga(UUID idPonto) {
        return idPontoA.equals(idPonto) || idPontoB.equals(idPonto);
    }
}
