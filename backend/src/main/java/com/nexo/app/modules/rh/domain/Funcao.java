package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Representa as funções/cargos que um colaborador pode assumir.
 * Permite gestão dinâmica de papéis no sistema.
 */
@Entity
@Table(name = "funcoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Funcao extends BaseEntity {

    @NotBlank(message = "O nome da função é obrigatório")
    @Size(min = 3, max = 100, message = "O nome da função deve ter entre 3 e 100 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 100)
    private String nome;

    @Column(name = "descricao")
    private String descricao;

    public Funcao(String nome, String descricao) {
        this.nome = Objects.requireNonNull(nome, "Nome da função é obrigatório").toUpperCase().trim();
        this.descricao = descricao;
    }

    public void atualizar(String nome, String descricao) {
        validarStatus();
        this.nome = Objects.requireNonNull(nome, "Nome da função é obrigatório").toUpperCase();
        this.descricao = descricao;
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
