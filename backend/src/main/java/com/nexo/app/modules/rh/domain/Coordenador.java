package com.nexo.app.modules.rh.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Representa um colaborador no papel de Coordenador com período de vigência.
 */
@Entity
@Table(name = "coordenadores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coordenador extends BaseEntity {

    @NotNull(message = "O colaborador é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_colaborador", nullable = false)
    private Colaborador colaborador;

    @NotNull(message = "A data de início é obrigatória")
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    public Coordenador(Colaborador colaborador, LocalDate dataInicio, LocalDate dataFim) {
        validarColaborador(colaborador);
        validarDatas(dataInicio, dataFim);
        this.colaborador = colaborador;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public void atualizar(LocalDate dataInicio, LocalDate dataFim) {
        validarStatus();
        validarDatas(dataInicio, dataFim);
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public void atualizarColaborador(Colaborador novoColaborador) {
        validarStatus();
        validarColaborador(novoColaborador);
        this.colaborador = novoColaborador;
    }

    public void encerrar(LocalDate dataFim) {
        validarStatus();
        LocalDate fim = dataFim != null ? dataFim : LocalDate.now();
        validarDatas(this.dataInicio, fim);
        this.dataFim = fim;
    }

    public void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um coordenador desativado.");
        }
    }

    private void validarColaborador(Colaborador colaborador) {
        Objects.requireNonNull(colaborador, "Colaborador é obrigatório");
        if (!colaborador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um colaborador desativado como coordenador.");
        }
    }

    private void validarDatas(LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(inicio, "Data de início é obrigatória");
        if (fim != null && fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("A data final não pode ser anterior à data inicial.");
        }
    }
}
