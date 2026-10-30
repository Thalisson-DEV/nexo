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
@Table(name = "motivo_desconto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotivoDesconto extends BaseEntity {

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "teto_parcelas", nullable = false)
    private Integer tetoParcelas;

    public MotivoDesconto(String descricao, Integer tetoParcelas) {
        if (!StringUtils.hasText(descricao)) {
            throw new IllegalArgumentException("A descrição do motivo de desconto não pode ser vazia.");
        }
        if (tetoParcelas == null || tetoParcelas < 1) {
            throw new IllegalArgumentException("O teto de parcelas deve ser maior que zero.");
        }
        this.descricao = descricao;
        this.tetoParcelas = tetoParcelas;
    }

    public void atualizar(String descricao, Integer tetoParcelas) {
        if (!StringUtils.hasText(descricao)) {
            throw new IllegalArgumentException("A descrição do motivo de desconto não pode ser vazia.");
        }
        if (tetoParcelas == null || tetoParcelas < 1) {
            throw new IllegalArgumentException("O teto de parcelas deve ser maior que zero.");
        }
        this.descricao = descricao;
        this.tetoParcelas = tetoParcelas;
    }
}
