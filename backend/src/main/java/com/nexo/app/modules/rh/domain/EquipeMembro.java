package com.nexo.app.modules.rh.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Entidade associativa rica para gerir o histórico de alocação de colaboradores em equipes.
 * Permite analisar a rotatividade através das datas de entrada e saída.
 */
@Entity
@Table(name = "equipe_membros")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipeMembro extends BaseEntity {

    @JsonBackReference
    @NotNull(message = "A equipe é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_equipe", nullable = false)
    private Equipe equipe;

    @NotNull(message = "O colaborador é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_colaborador", nullable = false)
    private Colaborador colaborador;

    @NotNull(message = "A data de entrada é obrigatória")
    @Column(name = "data_entrada", nullable = false)
    private OffsetDateTime dataEntrada;

    @Column(name = "data_saida")
    private OffsetDateTime dataSaida;

    @NotNull(message = "A função na equipe é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(name = "funcao", nullable = false)
    private FuncaoMembro funcao;

    public EquipeMembro(Equipe equipe, Colaborador colaborador, FuncaoMembro funcao) {
        this.equipe = Objects.requireNonNull(equipe, "Equipe é obrigatória");
        this.colaborador = Objects.requireNonNull(colaborador, "Colaborador é obrigatório");
        this.funcao = Objects.requireNonNull(funcao, "Função é obrigatória");
        this.dataEntrada = OffsetDateTime.now();
    }

    /**
     * Finaliza o vínculo do colaborador com a equipe (Histórico).
     */
    public void encerrarVinculo() {
        if (this.dataSaida != null) {
            throw new IllegalStateException("Vínculo já encerrado");
        }
        this.dataSaida = OffsetDateTime.now();
    }

    public boolean isVinculoAtivo() {
        return this.dataSaida == null;
    }

    public enum FuncaoMembro {
        RESPONSAVEL,
        PARCEIRO
    }
}
