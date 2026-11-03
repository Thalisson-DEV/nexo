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

import java.util.Objects;
import java.util.UUID;

/**
 * Fato registrado no turno, com tipo e, quando faz sentido, a obra em que aconteceu.
 */
@Entity
@Table(name = "turno_ocorrencias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OcorrenciaTurno extends BaseEntity {

    @Column(name = "id_turno", nullable = false, updatable = false)
    private UUID idTurno;

    @Column(name = "id_obra")
    private UUID idObra;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoOcorrenciaTurno tipo;

    @Column(name = "descricao", nullable = false, length = 2000)
    private String descricao;

    /** O serviço da obra não tem condição de ser feito neste turno: dispensa a APR e bloqueia o apontamento. */
    @Column(name = "impede_execucao", nullable = false)
    private boolean impedeExecucao;

    public OcorrenciaTurno(UUID idTurno, UUID idObra, TipoOcorrenciaTurno tipo, String descricao, boolean impedeExecucao) {
        this.idTurno = Objects.requireNonNull(idTurno);
        informar(idObra, tipo, descricao, impedeExecucao);
    }

    public void informar(UUID idObra, TipoOcorrenciaTurno tipo, String descricao, boolean impedeExecucao) {
        if (tipo == null) {
            throw new InvarianteVioladaException("Informe o tipo da ocorrência.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new InvarianteVioladaException("Descreva a ocorrência.");
        }
        if (impedeExecucao && idObra == null) {
            throw new InvarianteVioladaException("Informe a obra em que o serviço não tem condição de ser feito.");
        }
        this.idObra = idObra;
        this.tipo = tipo;
        this.descricao = descricao.trim();
        this.impedeExecucao = impedeExecucao;
    }
}
