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
 * Vínculo de um colaborador que está sendo inspecionado em uma {@link InspecaoSeguranca}.
 */
@Entity
@Table(name = "inspecao_colaboradores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ColaboradorInspecionado extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inspecao", nullable = false)
    private InspecaoSeguranca inspecao;

    @NotNull
    @Column(name = "id_colaborador", nullable = false)
    private UUID idColaborador;

    ColaboradorInspecionado(InspecaoSeguranca inspecao, UUID idColaborador) {
        this.inspecao = Objects.requireNonNull(inspecao, "Inspeção é obrigatória");
        this.idColaborador = Objects.requireNonNull(idColaborador, "ID do colaborador é obrigatório");
    }
}
