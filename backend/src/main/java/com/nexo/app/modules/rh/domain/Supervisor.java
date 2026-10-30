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
 * Representa um colaborador no papel de Supervisor que responde a um Coordenador,
 * com período de vigência.
 */
@Entity
@Table(name = "supervisores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Supervisor extends BaseEntity {

    @NotNull(message = "O colaborador é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_colaborador", nullable = false)
    private Colaborador colaborador;

    @NotNull(message = "O coordenador responsável é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_coordenador", nullable = false)
    private Coordenador coordenador;

    @NotNull(message = "A data de início é obrigatória")
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    public Supervisor(Colaborador colaborador, Coordenador coordenador, LocalDate dataInicio, LocalDate dataFim) {
        validarEntidades(colaborador, coordenador);
        validarDatas(dataInicio, dataFim);
        this.colaborador = colaborador;
        this.coordenador = coordenador;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public void atualizar(Coordenador coordenador, LocalDate dataInicio, LocalDate dataFim) {
        validarStatus();
        validarEntidades(this.colaborador, coordenador);
        validarDatas(dataInicio, dataFim);
        this.coordenador = coordenador;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public void atualizarColaborador(Colaborador novoColaborador) {
        validarStatus();
        validarEntidades(novoColaborador, this.coordenador);
        this.colaborador = novoColaborador;
    }

    public void trocarCoordenador(Coordenador novoCoordenador) {
        validarStatus();
        validarEntidades(this.colaborador, novoCoordenador);
        this.coordenador = novoCoordenador;
    }

    public void encerrar(LocalDate dataFim) {
        validarStatus();
        LocalDate fim = dataFim != null ? dataFim : LocalDate.now();
        validarDatas(this.dataInicio, fim);
        this.dataFim = fim;
    }

    public void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível alterar um supervisor desativado.");
        }
    }

    private void validarEntidades(Colaborador colaborador, Coordenador coordenador) {
        Objects.requireNonNull(colaborador, "Colaborador é obrigatório");
        Objects.requireNonNull(coordenador, "Coordenador é obrigatório");

        if (!colaborador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um colaborador desativado como supervisor.");
        }
        if (!coordenador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um coordenador desativado.");
        }
        if (colaborador.getId() != null && coordenador.getColaborador() != null 
                && colaborador.getId().equals(coordenador.getColaborador().getId())) {
            throw new InvarianteVioladaException("Um colaborador não pode ser supervisor de si mesmo sob sua própria coordenação.");
        }
    }

    private void validarDatas(LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(inicio, "Data de início é obrigatória");
        if (fim != null && fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("A data final não pode ser anterior à data inicial.");
        }
    }
}
