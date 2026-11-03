package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Item planejado dentro de uma programação, com quantidade e valor congelados na confirmação.
 * Material não tem valor: só o serviço é valorado.
 */
@Entity
@Table(name = "programacao_itens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramacaoItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_programacao", nullable = false)
    private Programacao programacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_item_planejado", nullable = false)
    private ItemPlanejado itemPlanejado;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", precision = 15, scale = 4)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", precision = 15, scale = 2)
    private BigDecimal valorTotal;

    ProgramacaoItem(Programacao programacao, ItemPlanejado item, BigDecimal valorUnitario) {
        this.programacao = programacao;
        this.itemPlanejado = item;
        this.quantidade = item.getQuantidadePrevista();
        this.valorUnitario = valorUnitario;
        this.valorTotal = valorUnitario != null
                ? valorUnitario.multiply(quantidade).setScale(2, RoundingMode.HALF_UP)
                : null;
    }
}
