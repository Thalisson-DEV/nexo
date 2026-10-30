package com.nexo.app.modules.desconto.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "tipo_equipamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoEquipamento extends BaseEntity {

    @Column(name = "descricao", nullable = false)
    private String descricao;

    public TipoEquipamento(String descricao) {
        if (!StringUtils.hasText(descricao)) {
            throw new IllegalArgumentException("A descrição do tipo de equipamento não pode ser vazia.");
        }
        this.descricao = descricao;
    }

    public void atualizar(String descricao) {
        if (!StringUtils.hasText(descricao)) {
            throw new IllegalArgumentException("A descrição do tipo de equipamento não pode ser vazia.");
        }
        this.descricao = descricao;
    }
}
