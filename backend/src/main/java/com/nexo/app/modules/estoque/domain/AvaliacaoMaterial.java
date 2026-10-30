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
 * Representa a avaliação técnica de um material (ex: INVEST, MANUT, RECUP, SUCATA).
 */
@Entity
@Table(name = "avaliacoes_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvaliacaoMaterial extends BaseEntity {

    @NotBlank(message = "O nome da avaliação é obrigatório")
    @Size(min = 3, max = 50, message = "O nome deve ter entre 3 e 50 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 50)
    private String nome;

    public AvaliacaoMaterial(String nome) {
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
            throw new IllegalArgumentException("Nome da avaliação técnica inválido");
        }
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
