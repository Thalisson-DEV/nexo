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

/**
 * Representa um Centro de Faturamento/Regional Comercial.
 * Ex: NORTE, CENTRO-NORTE.
 */
@Entity
@Table(name = "centros_faturamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CentroFaturamento extends BaseEntity {

    @NotBlank(message = "O nome do centro de faturamento é obrigatório")
    @Size(min = 3, max = 100, message = "O nome do centro de faturamento deve ter entre 3 e 100 caracteres")
    @Column(name = "nome", nullable = false, unique = true, length = 100)
    private String nome;

    public CentroFaturamento(String nome) {
        validarNome(nome);
        this.nome = nome.toUpperCase();
    }

    public void atualizar(String novoNome) {
        validarStatus();
        validarNome(novoNome);
        this.nome = novoNome.toUpperCase();
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do centro de faturamento inválido");
        }
    }
}
