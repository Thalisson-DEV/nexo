package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Relatório diário do turno: um por turno. Turno fechado sem ele aparece como "Sem relatório".
 */
@Entity
@Table(name = "turno_relatorios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelatorioTurno extends BaseEntity {

    @Column(name = "id_turno", nullable = false, updatable = false)
    private UUID idTurno;

    @Column(name = "observacao", nullable = false, length = 4000)
    private String observacao;

    public RelatorioTurno(UUID idTurno, String observacao) {
        this.idTurno = Objects.requireNonNull(idTurno);
        informar(observacao);
    }

    public void informar(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new InvarianteVioladaException("Informe a observação geral do relatório.");
        }
        this.observacao = texto.trim();
    }
}
