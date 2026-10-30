package com.nexo.app.modules.contrato.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.validators.ValidarIntervaloData;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Define o valor base de uma ação para um contrato específico em uma data de vigência.
 */
@Entity
@Table(name = "acoes_valores", uniqueConstraints = {
    @UniqueConstraint(name = "unq_acao_contrato_vigencia", columnNames = {"id_acao", "id_contrato", "data_vigencia"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcaoValor extends BaseEntity {

    @NotNull(message = "A ação é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_acao", nullable = false)
    private Acao acao;

    @NotNull(message = "O contrato é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contrato", nullable = false)
    private Contrato contrato;

    @NotNull(message = "O valor base é obrigatório")
    @Column(name = "valor_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorBase;

    @ValidarIntervaloData
    @NotNull(message = "A data de vigência é obrigatória")
    @Column(name = "data_vigencia", nullable = false)
    private LocalDate dataVigencia;

    public AcaoValor(Acao acao, Contrato contrato, BigDecimal valorBase, LocalDate dataVigencia) {
        this.acao = Objects.requireNonNull(acao, "Ação é obrigatória");
        this.contrato = Objects.requireNonNull(contrato, "Contrato é obrigatório");
        this.dataVigencia = Objects.requireNonNull(dataVigencia, "Data de vigência é obrigatória");
        validarValor(valorBase);
        this.valorBase = valorBase;

        if (!acao.isAtivo() || !contrato.isAtivo()) {
            throw new InvarianteVioladaException("Ação e Contrato devem estar ativos");
        }
    }

    public void atualizarValor(BigDecimal novoValor) {
        validarStatus();
        validarValor(novoValor);
        this.valorBase = novoValor;
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um registro desativado.");
        }
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvarianteVioladaException("O valor base não pode ser negativo");
        }
    }
}
