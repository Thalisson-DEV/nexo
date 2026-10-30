package com.nexo.app.modules.execucao.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.application.dto.OperacaoMaterial;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa o uso ou retirada de material em uma execução comercial.
 */
@Entity
@Table(name = "execucoes_materiais")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecucaoMaterial extends BaseEntity {

    @JsonBackReference
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_execucao", nullable = false)
    private ExecucaoComercial execucao;

    @NotNull
    @Column(name = "id_material", nullable = false)
    private UUID idMaterial;

    @NotNull
    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operacao", nullable = false, length = 20)
    private OperacaoMaterial operacao;

    @Column(name = "num_serie")
    private String numSerie;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemMaterial origem = OrigemMaterial.EQUIPE;

    public ExecucaoMaterial(ExecucaoComercial execucao, UUID idMaterial, BigDecimal quantidade, OperacaoMaterial operacao, String numSerie) {
        this(execucao, idMaterial, quantidade, operacao, numSerie, OrigemMaterial.EQUIPE);
    }

    public ExecucaoMaterial(ExecucaoComercial execucao, UUID idMaterial, BigDecimal quantidade, OperacaoMaterial operacao,
                            String numSerie, OrigemMaterial origem) {
        this.execucao = Objects.requireNonNull(execucao, "Execução é obrigatória");
        this.idMaterial = Objects.requireNonNull(idMaterial, "ID do material é obrigatório");
        this.quantidade = Objects.requireNonNull(quantidade, "Quantidade é obrigatória");
        this.operacao = Objects.requireNonNull(operacao, "Operação é obrigatória");
        this.numSerie = numSerie;
        this.origem = Objects.requireNonNull(origem, "Origem é obrigatória");
    }

    public MaterialLancado comoLancado() {
        return new MaterialLancado(idMaterial, quantidade, operacao, numSerie, origem);
    }

    public void atualizarQuantidade(BigDecimal novaQuantidade) {
        this.quantidade = Objects.requireNonNull(novaQuantidade, "A nova quantidade é obrigatória");
    }
}
