package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Representa um veículo da frota operacional.
 * Vinculado a uma Base Operacional específica.
 */
@Entity
@Table(name = "veiculos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Veiculo extends BaseEntity {

    @NotBlank(message = "A placa é obrigatória")
    @Size(min = 3, max = 10, message = "A placa deve ter entre 3 e 10 caracteres")
    @Column(name = "placa", nullable = false, unique = true, length = 10)
    private String placa;

    @NotBlank(message = "O modelo é obrigatório")
    @Size(min = 3, max = 100, message = "O modelo deve ter entre 3 e 100 caracteres")
    @Column(name = "modelo", nullable = false, length = 100)
    private String modelo;

    @NotBlank(message = "O fabricante é obrigatório")
    @Column(name = "fabricante", length = 50)
    @Size(min = 3, max = 50, message = "O fabricante deve ter entre 3 e 50 caracteres")
    private String fabricante;

    @NotNull(message = "A base operacional é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_base_operacional", nullable = false)
    private BaseOperacional baseOperacional;

    public Veiculo(String placa, String modelo, String fabricante, BaseOperacional baseOperacional) {
        validarCampos(placa, modelo, fabricante, baseOperacional);
        this.placa = placa.toUpperCase().replace("-", "").trim();
        this.modelo = modelo.toUpperCase();
        this.fabricante = fabricante.toUpperCase();
        this.baseOperacional = baseOperacional;
    }

    public void atualizar(String placa, String modelo, String fabricante, BaseOperacional baseOperacional) {
        validarCampos(placa, modelo, fabricante, baseOperacional);
        validarStatus();
        this.placa = placa.toUpperCase().replace("-", "").trim();
        this.modelo = modelo.toUpperCase();
        this.fabricante = fabricante.toUpperCase();
        this.baseOperacional = baseOperacional;
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void validarCampos(String placa, String modelo, String fabricante, BaseOperacional baseOperacional) {
        if (placa == null || placa.isBlank()) throw new IllegalArgumentException("Placa inválida");
        if (modelo == null || modelo.isBlank()) throw new IllegalArgumentException("Modelo inválido");
        if (fabricante == null || fabricante.isBlank()) throw new IllegalArgumentException("Fabricante inválido");
        if (baseOperacional == null) throw new IllegalArgumentException("Base operacional é obrigatória");
    }
}
