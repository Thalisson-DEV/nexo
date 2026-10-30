package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Snapshot imutável de cada item afetado por uma operação técnica. */
@Entity
@Table(name = "itens_operacao_solicitacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemOperacaoSolicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_operacao", nullable = false, updatable = false)
    private OperacaoSolicitacaoMaterial operacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_item_solicitacao", nullable = false, updatable = false)
    private ItemSolicitacao itemSolicitacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_material", nullable = false, updatable = false)
    private Material material;

    @Column(name = "codigo_material_snapshot", nullable = false, length = 100, updatable = false)
    private String codigoMaterialSnapshot;

    @Column(name = "descricao_material_snapshot", nullable = false, length = 355, updatable = false)
    private String descricaoMaterialSnapshot;

    @Column(name = "quantidade", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal quantidade;

    @Column(name = "num_serie", length = 255, updatable = false)
    private String numeroSerie;

    @Enumerated(EnumType.STRING)
    @Column(name = "condicao", nullable = false, length = 20, updatable = false)
    private CondicaoMaterial condicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_item_operacao_original", updatable = false)
    private ItemOperacaoSolicitacao itemOperacaoOriginal;

    ItemOperacaoSolicitacao(
            OperacaoSolicitacaoMaterial operacao,
            ItemSolicitacao itemSolicitacao,
            BigDecimal quantidade,
            String numeroSerie,
            CondicaoMaterial condicao,
            ItemOperacaoSolicitacao itemOperacaoOriginal
    ) {
        this.operacao = Objects.requireNonNull(operacao, "A operacao e obrigatoria");
        this.itemSolicitacao = Objects.requireNonNull(itemSolicitacao, "O item da solicitacao e obrigatorio");
        this.material = Objects.requireNonNull(itemSolicitacao.getMaterial(), "O material e obrigatorio");
        this.codigoMaterialSnapshot = material.getCodigo();
        this.descricaoMaterialSnapshot = material.getDescricao();
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new InvarianteVioladaException("A quantidade operacional deve ser positiva.");
        }
        this.quantidade = quantidade;
        this.numeroSerie = numeroSerie == null || numeroSerie.isBlank() ? null : numeroSerie.trim();
        this.condicao = Objects.requireNonNullElse(condicao, CondicaoMaterial.NOVO);
        this.itemOperacaoOriginal = itemOperacaoOriginal;
    }
}
