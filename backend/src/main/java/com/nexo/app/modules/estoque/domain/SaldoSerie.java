package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Representa a localização atual de um item individual (rastreado por número de série) 
 * dentro de um estoque. Complementa o Saldo quantitativo.
 */
@Entity
@Table(name = "saldos_seriais")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaldoSerie extends BaseEntity {

    @NotNull(message = "O estoque é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estoque", nullable = false)
    private Estoque estoque;

    @NotNull(message = "O material é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_material", nullable = false)
    private Material material;

    @NotBlank(message = "O número de série é obrigatório")
    @Column(name = "num_serie", nullable = false, length = 100)
    private String numSerie;

    @NotNull(message = "A condição do material é obrigatória")
    @Enumerated(EnumType.STRING)
    @Column(name = "condicao", nullable = false, length = 20)
    private CondicaoMaterial condicao;

    /** Lado inverso: reservar não altera esta linha (nem a data que marca a chegada ao estoque). */
    @OneToMany(mappedBy = "saldoSerie")
    @BatchSize(size = 100)
    private List<ReservaSerieSolicitacao> reservas = new ArrayList<>();

    public SaldoSerie(Estoque estoque, Material material, String numSerie, CondicaoMaterial condicao) {
        validarVinculos(estoque, material, numSerie, condicao);
        this.estoque = estoque;
        this.material = material;
        this.numSerie = numSerie;
        this.condicao = condicao != null ? condicao : CondicaoMaterial.NOVO;
    }

    /**
     * Atualiza a localização do item serializado (ex: Transferência).
     */
    public void transferirPara(Estoque novoEstoque) {
        validarStatus();
        exigirSemReserva();
        if (novoEstoque == null) {
            throw new IllegalArgumentException("O novo estoque de destino é obrigatório.");
        }
        if (!novoEstoque.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível transferir um serial para um estoque desativado.");
        }
        this.estoque = novoEstoque;
    }

    /**
     * Altera a condição do item (ex: de NOVO para SUCATA).
     */
    public void alterarCondicao(CondicaoMaterial novaCondicao) {
        validarStatus();
        if (novaCondicao == null) {
            throw new IllegalArgumentException("A nova condição é obrigatória.");
        }
        this.condicao = novaCondicao;
    }

    /** Baixa, aplicação ou saída definitiva da série; recusa se ela estiver reservada. */
    @Override
    public void desativar() {
        exigirSemReserva();
        super.desativar();
    }

    /** A reserva que hoje prende a série a uma solicitação, se houver. */
    public Optional<ReservaSerieSolicitacao> getReservaVigente() {
        return reservas.stream().filter(ReservaSerieSolicitacao::isVigente).findFirst();
    }

    /**
     * Na entrega da própria solicitação a reserva se cumpre: deixa de valer e a série pode sair.
     * Reserva de outra solicitação continua valendo e a saída é recusada.
     */
    public void cumprirReservaDa(SolicitacaoMaterial solicitacao) {
        reservas.stream()
                .filter(BaseEntity::isAtivo)
                .filter(r -> r.getSolicitacao().getId().equals(solicitacao.getId()))
                .forEach(BaseEntity::desativar);
    }

    /** Fato consumado (ex.: aplicação em campo): nenhuma reserva pode impedir o registro. */
    public void liberarReservas() {
        reservas.stream().filter(BaseEntity::isAtivo).forEach(BaseEntity::desativar);
    }

    private void exigirSemReserva() {
        getReservaVigente().ifPresent(r -> {
            throw new InvarianteVioladaException("A série " + numSerie + " está reservada para a solicitação #"
                    + r.getSolicitacao().getId().toString().substring(0, 8) + ".");
        });
    }

    private void validarVinculos(Estoque estoque, Material material, String numSerie, CondicaoMaterial condicao) {
        if (estoque == null || material == null || numSerie == null || numSerie.isBlank() || condicao == null) {
            throw new IllegalArgumentException("Estoque, Material, Número de Série e Condição são obrigatórios.");
        }
        if (!material.isDetalhaSerie()) {
            throw new InvarianteVioladaException("O material '" + material.getCodigo() + "' não permite controle por número de série.");
        }
        if (!estoque.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível criar saldo serial para um estoque desativado.");
        }
        if (!material.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível criar saldo serial para um material desativado.");
        }
    }

    private void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalStateException("Não é possível movimentar um saldo serial desativado.");
        }
    }
}
