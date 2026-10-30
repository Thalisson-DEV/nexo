package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Representa um município. 
 * Referencia o módulo de RH via UUID para manter o isolamento.
 */
@Entity
@Table(name = "municipios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Municipio extends BaseEntity {

    @NotBlank(message = "O nome do município é obrigatório")
    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @NotNull(message = "A base operacional é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_base_operacional", nullable = false)
    private BaseOperacional baseOperacional;

    public Municipio(String nome, BaseOperacional baseOperacional) {
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("Nome inválido");
        this.nome = nome.toUpperCase();
        this.baseOperacional = Objects.requireNonNull(baseOperacional, "A base operacional é obrigatória");
    }

    public void atualizar(String novoNome, BaseOperacional novaBase) {
        validarStatus();
        if (novoNome == null || novoNome.isBlank()) throw new IllegalArgumentException("Nome inválido");
        this.nome = novoNome.toUpperCase();
        this.baseOperacional = Objects.requireNonNull(novaBase, "A base operacional é obrigatória");
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }
}
