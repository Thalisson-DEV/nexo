package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "obras_relacionamentos", uniqueConstraints =
        @UniqueConstraint(name = "uq_obras_relacionamentos_par", columnNames = {"id_projeto_a", "id_projeto_b"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelacionamentoProjeto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_projeto_a", nullable = false)
    private Obra projetoA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_projeto_b", nullable = false)
    private Obra projetoB;

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "tipo", nullable = false, length = 20)
    private TipoVinculoProjeto tipo;

    public RelacionamentoProjeto(Obra primeiroProjeto, Obra segundoProjeto, TipoVinculoProjeto tipo) {
        validarProjetos(primeiroProjeto, segundoProjeto);
        ordenarParCanonico(primeiroProjeto, segundoProjeto);
        this.tipo = Objects.requireNonNull(tipo, "O tipo do vínculo entre projetos é obrigatório.");
    }

    public boolean contemProjeto(UUID idProjeto) {
        return idProjeto != null
                && (idProjeto.equals(projetoA.getId()) || idProjeto.equals(projetoB.getId()));
    }

    public Obra projetoRelacionadoA(UUID idProjeto) {
        if (!contemProjeto(idProjeto)) {
            throw new InvarianteVioladaException("O projeto informado não pertence ao relacionamento.");
        }
        return idProjeto.equals(projetoA.getId()) ? projetoB : projetoA;
    }

    public void alterarTipo(TipoVinculoProjeto novoTipo) {
        this.tipo = Objects.requireNonNull(novoTipo, "O tipo do vínculo entre projetos é obrigatório.");
    }

    private void validarProjetos(Obra primeiroProjeto, Obra segundoProjeto) {
        if (primeiroProjeto == null || segundoProjeto == null) {
            throw new InvarianteVioladaException("Os dois projetos do relacionamento são obrigatórios.");
        }
        if (primeiroProjeto.getId() == null || segundoProjeto.getId() == null) {
            throw new InvarianteVioladaException("Os projetos devem estar persistidos antes do relacionamento.");
        }
        if (primeiroProjeto.getId().equals(segundoProjeto.getId())) {
            throw new InvarianteVioladaException("Um projeto não pode ser relacionado a si mesmo.");
        }
    }

    private void ordenarParCanonico(Obra primeiroProjeto, Obra segundoProjeto) {
        if (primeiroProjeto.getId().toString().compareTo(segundoProjeto.getId().toString()) < 0) {
            this.projetoA = primeiroProjeto;
            this.projetoB = segundoProjeto;
        } else {
            this.projetoA = segundoProjeto;
            this.projetoB = primeiroProjeto;
        }
    }
}
