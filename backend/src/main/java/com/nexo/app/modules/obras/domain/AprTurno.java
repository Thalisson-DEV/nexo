package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Análise preliminar de riscos: o formulário que a equipe preenche durante o dia de trabalho, antes de executar um
 * serviço. Sem APR concluída do serviço no dia, não há apontamento. Depois de concluída, não muda mais.
 */
@Entity
@Table(name = "turno_aprs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AprTurno extends BaseEntity {

    @Column(name = "id_turno", nullable = false, updatable = false)
    private UUID idTurno;

    @Column(name = "id_obra")
    private UUID idObra;

    /** O serviço programado que esta APR libera; nula em atividade de obra não programada. */
    @Column(name = "id_programacao")
    private UUID idProgramacao;

    @Column(name = "atividade", nullable = false, length = 500)
    private String atividade;

    @Column(name = "id_responsavel")
    private UUID idResponsavel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusAprTurno status = StatusAprTurno.RASCUNHO;

    @Column(name = "data_conclusao")
    private OffsetDateTime dataConclusao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "formulario", columnDefinition = "jsonb")
    private FormularioApr formulario;

    public AprTurno(UUID idTurno, UUID idObra, UUID idProgramacao, String atividade, UUID idResponsavel, FormularioApr formulario) {
        this.idTurno = Objects.requireNonNull(idTurno);
        informar(idObra, idProgramacao, atividade, idResponsavel, formulario);
    }

    public void informar(UUID idObra, UUID idProgramacao, String atividade, UUID idResponsavel, FormularioApr formulario) {
        if (status == StatusAprTurno.CONCLUIDA) {
            throw new InvarianteVioladaException("A APR já foi concluída e não pode mais ser alterada.");
        }
        if (idObra == null) {
            throw new InvarianteVioladaException("Informe a obra da APR.");
        }
        if (atividade == null || atividade.isBlank()) {
            throw new InvarianteVioladaException("Informe a atividade da APR.");
        }
        this.idObra = idObra;
        this.idProgramacao = idProgramacao;
        this.atividade = atividade.trim();
        this.idResponsavel = idResponsavel;
        this.formulario = formulario;
    }

    public void concluir() {
        if (idResponsavel == null) {
            throw new InvarianteVioladaException("Informe o responsável antes de concluir a APR.");
        }
        if (formulario == null) {
            throw new InvarianteVioladaException("Preencha o formulário antes de concluir a APR.");
        }
        formulario.validarConclusao();
        if (status == StatusAprTurno.RASCUNHO) {
            this.status = StatusAprTurno.CONCLUIDA;
            this.dataConclusao = OffsetDateTime.now();
        }
    }

    public void excluir() {
        if (status == StatusAprTurno.CONCLUIDA) {
            throw new InvarianteVioladaException("A APR já foi concluída e não pode ser excluída.");
        }
        desativar();
    }
}
