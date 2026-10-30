package com.nexo.app.modules.iam.domain;

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
 * Representa uma permissão granular no sistema (Authority).
 * Segue o padrão 'dominio:recurso:acao'.
 */
@Entity
@Table(name = "permissoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permissao extends BaseEntity {

    @NotBlank(message = "O nome da permissão é obrigatório")
    @Size(min = 3, max = 100, message = "O nome da permissão deve ter entre 3 e 100 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 100)
    private String nome;

    public Permissao(String nome) {
        validarNome(nome);
        this.nome = nome.toLowerCase().trim();
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da permissão inválido");
        }
    }

    public void atualizarNome(String novoNome) {
        validarNome(novoNome);
        this.nome = novoNome.toLowerCase().trim();
    }
}
