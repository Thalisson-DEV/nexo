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
 * Linha da lista de equipamentos: o ponto da obra com religador ou regulador.
 */
@Entity
@Table(name = "equipamentos_obra")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipamentoObra extends BaseEntity {

    public enum Tipo {
        RELIGADOR,
        REGULADOR
    }

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "id_ponto", nullable = false, updatable = false)
    private UUID idPonto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private Tipo tipo;

    @Column(name = "trafo_auxiliar", nullable = false)
    private boolean trafoAuxiliar;

    @Column(name = "observacao", length = 500)
    private String observacao;

    public EquipamentoObra(UUID idObra, UUID idPonto, Tipo tipo, boolean trafoAuxiliar, String observacao) {
        this.idObra = Objects.requireNonNull(idObra);
        this.idPonto = Objects.requireNonNull(idPonto);
        informar(tipo, trafoAuxiliar, observacao);
    }

    public void informar(Tipo tipo, boolean trafoAuxiliar, String observacao) {
        if (tipo == null) {
            throw new InvarianteVioladaException("Informe o equipamento do ponto.");
        }
        if (trafoAuxiliar && tipo != Tipo.RELIGADOR) {
            throw new InvarianteVioladaException("Só o religador tem trafo auxiliar.");
        }
        this.tipo = tipo;
        this.trafoAuxiliar = trafoAuxiliar;
        this.observacao = observacao != null && !observacao.isBlank() ? observacao.trim() : null;
    }
}
