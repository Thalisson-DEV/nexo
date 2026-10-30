package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Representa o centro de investimento (ex: 670, 610).
 */
@Entity
@Table(name = "centros_investimento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CentroInvestimento extends BaseEntity {

    @NotBlank(message = "O código do centro de investimento é obrigatório")
    @Size(min = 3, max = 10, message = "O código deve ter entre 3 e 10 caracteres")
    @Column(name = "codigo", nullable = false, unique = true, length = 10)
    private String codigo;

    public CentroInvestimento(String codigo) {
        validarCodigo(codigo);
        this.codigo = codigo.toUpperCase().trim();
    }

    public void atualizarCodigo(String novoCodigo) {
        validarStatus();
        validarCodigo(novoCodigo);
        this.codigo = novoCodigo.toUpperCase().trim();
    }

    private void validarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Código do centro de investimento inválido");
        }
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
