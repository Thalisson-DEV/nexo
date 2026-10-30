package com.nexo.app.modules.contrato.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "contratos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contrato extends BaseEntity {

    @NotBlank(message = "Número do contrato é obrigatório")
    @Size(min = 3, max = 100, message = "O numero do contrato deve ter entre 3 e 100 caracteres")
    @Column(name = "numero_contrato", nullable = false, length = 100)
    private String numeroContrato;

    @NotBlank(message = "Cliente é obrigatório")
    @Size(min = 3, max = 100, message = "O cliente deve ter entre 3 e 100 caracteres")
    @Column(name = "cliente", nullable = false, length = 100)
    private String cliente;

    @NotNull(message = "Data de vigência é obrigatória")
    @Column(name = "data_vigencia", nullable = false)
    private LocalDate dataVigencia;

    @NotNull(message = "Data de encerramento é obrigatória")
    @Column(name = "data_encerramento", nullable = false)
    private LocalDate dataEncerramento;

    @NotNull(message = "O coeficiente de faturamento é obrigatório")
    @DecimalMin(value = "0.0001", message = "O coeficiente de faturamento deve ser maior que zero")
    @DecimalMax(value = "1000", message = "O coeficiente de faturamento deve ser menor ou igual a 1000")
    @Column(name = "coeficiente_faturamento", nullable = false, precision = 10, scale = 4)
    private BigDecimal coeficienteFaturamento;

    public Contrato(String numeroContrato, String cliente, LocalDate dataVigencia, LocalDate dataEncerramento, BigDecimal coeficienteFaturamento) {
        validarDatas(dataVigencia, dataEncerramento);
        validarCoeficiente(coeficienteFaturamento);
        this.numeroContrato = Objects.requireNonNull(numeroContrato, "Número do contrato é obrigatório").toUpperCase().trim();
        this.cliente = Objects.requireNonNull(cliente, "Cliente é obrigatório");
        this.dataVigencia = dataVigencia;
        this.dataEncerramento = dataEncerramento;
        this.coeficienteFaturamento = coeficienteFaturamento;
    }

    public void atualizarVigencia(LocalDate novaVigencia, LocalDate novoEncerramento) {
        validarStatus();
        validarDatas(novaVigencia, novoEncerramento);
        this.dataVigencia = novaVigencia;
        this.dataEncerramento = novoEncerramento;
    }

    public void atualizarDados(String numeroContrato, String cliente) {
        validarStatus();
        if (numeroContrato == null || numeroContrato.isBlank()) throw new IllegalArgumentException("Número do contrato inválido");
        if (cliente == null || cliente.isBlank()) throw new IllegalArgumentException("Cliente inválido");
        this.numeroContrato = numeroContrato.toUpperCase();
        this.cliente = cliente;
    }

    public void atualizarCoeficiente(BigDecimal novoCoeficiente) {
        validarStatus();
        validarCoeficiente(novoCoeficiente);
        this.coeficienteFaturamento = novoCoeficiente;
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void validarDatas(LocalDate vigencia, LocalDate encerramento) {
        if (vigencia == null || encerramento == null) {
            throw new IllegalArgumentException("Datas são obrigatórias");
        }
        if (vigencia.isAfter(encerramento)) {
            throw new InvarianteVioladaException("A data de vigência não pode ser posterior ao encerramento");
        }
    }

    private void validarCoeficiente(BigDecimal coeficiente) {
        if (coeficiente == null || coeficiente.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvarianteVioladaException("O coeficiente de faturamento deve ser maior que zero");
        }
        if (coeficiente.compareTo(BigDecimal.valueOf(1000)) > 0) {
            throw new InvarianteVioladaException("O coeficiente de faturamento deve ser menor ou igual a 1000");
        }
    }
}
