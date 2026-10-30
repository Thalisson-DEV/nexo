package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Colaborador responsabilizado por um {@link DesvioSeguranca}. Deve sempre ser um
 * dos colaboradores marcados como inspecionados na inspeção (invariante garantido
 * pela raiz do agregado {@link InspecaoSeguranca}).
 */
@Entity
@Table(name = "desvio_colaboradores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DesvioColaborador extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_desvio", nullable = false)
    private DesvioSeguranca desvio;

    @NotNull
    @Column(name = "id_colaborador", nullable = false)
    private UUID idColaborador;

    DesvioColaborador(DesvioSeguranca desvio, UUID idColaborador) {
        this.desvio = Objects.requireNonNull(desvio, "Desvio é obrigatório");
        this.idColaborador = Objects.requireNonNull(idColaborador, "ID do colaborador é obrigatório");
    }
}
