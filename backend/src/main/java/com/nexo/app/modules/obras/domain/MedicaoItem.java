package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Serviço dentro de uma medição. Guarda cópia de código, descrição, local e valores para o boletim continuar igual
 * mesmo que o planejamento mude depois. Fica ativo enquanto a medição não é estornada: é o que impede medir duas vezes.
 */
@Entity
@Table(name = "medicao_itens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicaoItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_medicao", nullable = false, updatable = false)
    private Medicao medicao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_item_planejado", nullable = false, updatable = false)
    private ItemPlanejado itemPlanejado;

    @Column(name = "id_kit_aplicado", nullable = false, updatable = false)
    private UUID idKitAplicado;

    @Column(name = "id_ponto", updatable = false)
    private UUID idPonto;

    @Column(name = "id_vao", updatable = false)
    private UUID idVao;

    @Column(name = "codigo_kit", length = 60, updatable = false)
    private String codigoKit;

    @Column(name = "codigo", length = 60, updatable = false)
    private String codigo;

    @Column(name = "descricao", length = 300, updatable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "operacao", nullable = false, length = 20, updatable = false)
    private OperacaoItemKit operacao;

    @Column(name = "unidade", length = 20, updatable = false)
    private String unidade;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 4, updatable = false)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", precision = 15, scale = 4, updatable = false)
    private BigDecimal valorUnitario;

    /** Nulo quando o serviço não tinha valor vigente ao ser programado. */
    @Column(name = "valor_total", precision = 15, scale = 2, updatable = false)
    private BigDecimal valorTotal;

    @Column(name = "concluido_em", updatable = false)
    private LocalDate concluidoEm;

    MedicaoItem(Medicao medicao, Medicao.ItemAMedir origem) {
        ItemPlanejado item = origem.item();
        item.medir();
        KitAplicado kit = item.getKitAplicado();
        this.medicao = medicao;
        this.itemPlanejado = item;
        this.idKitAplicado = kit.getId();
        this.idPonto = kit.getIdPonto();
        this.idVao = kit.getIdVao();
        this.codigoKit = kit.getCodigoKit();
        this.codigo = item.getCodigo();
        this.descricao = item.getDescricao();
        this.operacao = item.getOperacao();
        this.unidade = item.getUnidade();
        this.quantidade = origem.quantidade();
        this.valorTotal = origem.valorTotal() != null ? origem.valorTotal().setScale(2, RoundingMode.HALF_UP) : null;
        this.valorUnitario = valorTotal != null && quantidade.signum() > 0
                ? valorTotal.divide(quantidade, 4, RoundingMode.HALF_UP)
                : null;
        this.concluidoEm = origem.concluidoEm();
    }

    void estornar() {
        itemPlanejado.desfazerMedicao();
        desativar();
    }
}
