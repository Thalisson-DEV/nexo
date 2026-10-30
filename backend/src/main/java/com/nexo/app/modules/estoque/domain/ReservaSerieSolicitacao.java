package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Objects;
import java.util.UUID;

/**
 * Série definida pelo almoxarifado para um item da solicitação comercial antes da entrega.
 * Enquanto vale, a série não sai do estoque por outro caminho; na entrega, é só confirmar.
 */
@Entity
@Table(name = "reservas_serie_solicitacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservaSerieSolicitacao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_saldo_serie", nullable = false)
    private SaldoSerie saldoSerie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_item_solicitacao", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ItemSolicitacao itemSolicitacao;

    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    public ReservaSerieSolicitacao(SaldoSerie saldoSerie, ItemSolicitacao itemSolicitacao, UUID idUsuario) {
        this.saldoSerie = Objects.requireNonNull(saldoSerie, "A série é obrigatória");
        this.itemSolicitacao = Objects.requireNonNull(itemSolicitacao, "O item é obrigatório");
        this.idUsuario = Objects.requireNonNull(idUsuario, "O usuário é obrigatório");
    }

    /**
     * A reserva só prende a série enquanto o item ainda tem o que entregar, a solicitação aceita
     * entrega e a série segue no estoque de origem. Fora disso ela caduca sozinha: rejeitar,
     * cancelar, encerrar o saldo ou remover o item libera a série sem passo extra.
     */
    public boolean isVigente() {
        if (!isAtivo() || !saldoSerie.isAtivo() || !itemSolicitacao.isAtivo()) return false;
        SolicitacaoMaterial solicitacao = itemSolicitacao.getSolicitacao();
        return solicitacao.aceitaReservaDeSerie()
                && itemSolicitacao.getQuantidadePendente().signum() > 0
                && Objects.equals(saldoSerie.getEstoque().getId(), solicitacao.getEstoqueOrigem().getId());
    }

    public SolicitacaoMaterial getSolicitacao() {
        return itemSolicitacao.getSolicitacao();
    }
}
