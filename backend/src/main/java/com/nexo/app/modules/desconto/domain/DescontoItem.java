package com.nexo.app.modules.desconto.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Entity
@Table(name = "desconto_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DescontoItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desconto_id", nullable = false)
    private Desconto desconto;

    @Column(name = "descricao_material", nullable = false)
    private String descricaoMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_equipamento_id", nullable = false)
    private TipoEquipamento tipoEquipamento;

    @Column(name = "valor", nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    public DescontoItem(Desconto desconto, String descricaoMaterial, TipoEquipamento tipoEquipamento, BigDecimal valor) {
        if (desconto == null) throw new IllegalArgumentException("O desconto associado não pode ser nulo.");
        if (!StringUtils.hasText(descricaoMaterial)) throw new IllegalArgumentException("A descrição do material não pode ser vazia.");
        if (tipoEquipamento == null) throw new IllegalArgumentException("O tipo de equipamento não pode ser nulo.");
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("O valor não pode ser nulo ou negativo.");
        
        this.desconto = desconto;
        this.descricaoMaterial = descricaoMaterial;
        this.tipoEquipamento = tipoEquipamento;
        this.valor = valor;
    }

    public void atualizar(String descricaoMaterial, TipoEquipamento tipoEquipamento, BigDecimal valor) {
        if (!StringUtils.hasText(descricaoMaterial)) throw new IllegalArgumentException("A descrição do material não pode ser vazia.");
        if (tipoEquipamento == null) throw new IllegalArgumentException("O tipo de equipamento não pode ser nulo.");
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException("O valor não pode ser nulo ou negativo.");
        
        this.descricaoMaterial = descricaoMaterial;
        this.tipoEquipamento = tipoEquipamento;
        this.valor = valor;
    }
}
