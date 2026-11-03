package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pré-vistoria da obra, lançada pelo supervisor. O primeiro registro faz a obra passar a "visita realizada".
 */
@Entity
@Table(name = "visitas_previas_obra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitaPreviaObra extends BaseEntity {

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "id_modelo", nullable = false)
    private UUID idModelo;

    @Column(name = "titulo_modelo", nullable = false, length = 150)
    private String tituloModelo;

    @Column(name = "data_visita", nullable = false)
    private LocalDate dataVisita;

    @Column(name = "responsavel", nullable = false, length = 150)
    private String responsavel;

    @Column(name = "observacoes", length = 2000)
    private String observacoes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "respostas", nullable = false, columnDefinition = "jsonb")
    private RespostasChecklist respostas;

    public VisitaPreviaObra(UUID idObra, ModeloChecklistObra modelo, LocalDate dataVisita, String responsavel,
                            String observacoes, RespostasChecklist respostas) {
        this.idObra = Objects.requireNonNull(idObra);
        informar(modelo, dataVisita, responsavel, observacoes, respostas);
    }

    public void informar(ModeloChecklistObra modelo, LocalDate dataVisita, String responsavel, String observacoes, RespostasChecklist respostas) {
        if (modelo.getFinalidade() != ModeloChecklistObra.Finalidade.VISITA_PREVIA) {
            throw new InvarianteVioladaException("O modelo escolhido não é de visita prévia.");
        }
        if (dataVisita == null) {
            throw new InvarianteVioladaException("Informe a data da visita.");
        }
        if (responsavel == null || responsavel.isBlank()) {
            throw new InvarianteVioladaException("Informe o responsável pela visita.");
        }
        List<String> pendencias = respostas.pendencias();
        if (!pendencias.isEmpty()) {
            throw new InvarianteVioladaException("Responda antes de salvar: " + String.join("; ", pendencias) + ".");
        }
        this.idModelo = modelo.getId();
        this.tituloModelo = modelo.getTitulo();
        this.dataVisita = dataVisita;
        this.responsavel = responsavel.trim();
        this.observacoes = observacoes != null && !observacoes.isBlank() ? observacoes.trim() : null;
        this.respostas = respostas;
    }
}
