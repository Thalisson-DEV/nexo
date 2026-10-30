package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Período em que um implemento esteve acoplado a um veículo. */
@Entity
@Table(name = "frota_acoplamentos_implemento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcoplamentoImplemento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_implemento", nullable = false, updatable = false)
    private Implemento implemento;

    @Column(name = "id_veiculo", nullable = false, updatable = false)
    private UUID idVeiculo;

    @Column(name = "inicio", nullable = false, updatable = false)
    private OffsetDateTime inicio;

    @Column(name = "fim")
    private OffsetDateTime fim;

    @Column(name = "observacao", length = 500)
    private String observacao;

    public AcoplamentoImplemento(Implemento implemento, UUID idVeiculo, OffsetDateTime inicio, String observacao) {
        Objects.requireNonNull(implemento, "O implemento é obrigatório");
        implemento.exigirAcoplavel();
        if (idVeiculo == null) {
            throw new InvarianteVioladaException("Informe o veículo.");
        }
        this.implemento = implemento;
        this.idVeiculo = idVeiculo;
        this.inicio = Periodo.inicioValido(inicio);
        this.observacao = Textos.opcional(observacao, 500);
    }

    public boolean isAberto() {
        return fim == null;
    }

    public void encerrar(OffsetDateTime fim) {
        if (!isAberto()) {
            throw new InvarianteVioladaException("Este acoplamento já foi encerrado.");
        }
        this.fim = Periodo.fimValido(inicio, fim);
    }
}
