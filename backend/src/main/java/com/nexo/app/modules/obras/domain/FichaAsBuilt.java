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

import java.util.Objects;
import java.util.UUID;

/**
 * O ponto conforme construído. Pode ser salva pela metade; incompleta, não entra no documento gerado.
 */
@Entity
@Table(name = "fichas_as_built")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FichaAsBuilt extends BaseEntity {

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "id_ponto", nullable = false, updatable = false)
    private UUID idPonto;

    @Column(name = "id_modelo", nullable = false)
    private UUID idModelo;

    @Column(name = "titulo_modelo", nullable = false, length = 150)
    private String tituloModelo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "respostas", nullable = false, columnDefinition = "jsonb")
    private RespostasChecklist respostas;

    @Column(name = "completa", nullable = false)
    private boolean completa;

    public FichaAsBuilt(UUID idObra, UUID idPonto, ModeloChecklistObra modelo, RespostasChecklist respostas) {
        this.idObra = Objects.requireNonNull(idObra);
        this.idPonto = Objects.requireNonNull(idPonto);
        preencher(modelo, respostas);
    }

    public void preencher(ModeloChecklistObra modelo, RespostasChecklist respostas) {
        if (modelo.getFinalidade() != ModeloChecklistObra.Finalidade.AS_BUILT) {
            throw new InvarianteVioladaException("O modelo escolhido não é de ficha As Built.");
        }
        this.idModelo = modelo.getId();
        this.tituloModelo = modelo.getTitulo();
        this.respostas = respostas;
        this.completa = respostas.pendencias().isEmpty();
    }
}
