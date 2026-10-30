package com.nexo.app.modules.execucao.domain;

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
 * Representa uma ação vinculada a uma execução comercial.
 */
@Entity
@Table(name = "execucoes_acoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecucaoAcao extends BaseEntity {

    @JsonBackReference
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_execucao", nullable = false)
    private ExecucaoComercial execucao;

    @NotNull
    @Column(name = "id_acao", nullable = false)
    private UUID idAcao;

    public ExecucaoAcao(ExecucaoComercial execucao, UUID idAcao) {
        this.execucao = Objects.requireNonNull(execucao, "Execução é obrigatória");
        this.idAcao = Objects.requireNonNull(idAcao, "ID da ação é obrigatório");
    }
}
