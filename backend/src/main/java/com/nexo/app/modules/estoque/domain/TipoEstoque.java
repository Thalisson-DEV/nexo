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
 * Representa o tipo de estoque (ex: FISICO, CAMPO).
 */
@Entity
@Table(name = "tipos_estoque")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoEstoque extends BaseEntity {

    @NotBlank(message = "O nome do tipo de estoque é obrigatório")
    @Size(min = 3, max = 20, message = "O nome deve ter entre 3 e 20 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 20)
    private String nome;

    public TipoEstoque(String nome) {
        validarNome(nome);
        this.nome = nome.toUpperCase().trim();
    }

    public void atualizarNome(String novoNome) {
        validarStatus();
        validarNome(novoNome);
        this.nome = novoNome.toUpperCase().trim();
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do tipo de estoque inválido");
        }
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
