package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Obra em que a equipe atuou durante o turno. Sem programação, é atividade em obra não programada.
 */
@Entity
@Table(name = "turno_atividades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AtividadeTurno extends BaseEntity {

    @Column(name = "id_turno", nullable = false, updatable = false)
    private UUID idTurno;

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "id_programacao", updatable = false)
    private UUID idProgramacao;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    public AtividadeTurno(UUID idTurno, UUID idObra, UUID idProgramacao, String observacao) {
        this.idTurno = Objects.requireNonNull(idTurno);
        this.idObra = Objects.requireNonNull(idObra);
        this.idProgramacao = idProgramacao;
        this.observacao = observacao != null && !observacao.isBlank() ? observacao.trim() : null;
    }

    public boolean isProgramada() {
        return idProgramacao != null;
    }
}
