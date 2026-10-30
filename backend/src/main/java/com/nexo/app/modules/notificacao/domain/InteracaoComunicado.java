package com.nexo.app.modules.notificacao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

/**
 * O que um usuário fez com um comunicado.
 *
 * <p>A linha só nasce quando ele age — não há pré-criação para toda a audiência.
 * Isso é o que torna viável publicar para a empresa inteira: a escrita é
 * proporcional a quem interagiu, não a quem foi alcançado.</p>
 */
@Entity
@Table(name = "interacoes_comunicado")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteracaoComunicado extends BaseEntity {

    // Sem coleção correspondente no Comunicado de propósito: mapear as interações como
    // filhas faria o agregado carregar uma linha por usuário que já agiu.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_comunicado", nullable = false)
    private Comunicado comunicado;

    @Column(name = "id_usuario", nullable = false, updatable = false)
    private UUID idUsuario;

    @Column(name = "visualizado_em")
    private OffsetDateTime visualizadoEm;

    @Column(name = "dispensado_em")
    private OffsetDateTime dispensadoEm;

    @Column(name = "adiado_ate")
    private OffsetDateTime adiadoAte;

    @Column(name = "confirmado_em")
    private OffsetDateTime confirmadoEm;

    public InteracaoComunicado(Comunicado comunicado, UUID idUsuario) {
        if (comunicado == null) {
            throw new InvarianteVioladaException("O comunicado da interação é obrigatório.");
        }
        if (idUsuario == null) {
            throw new InvarianteVioladaException("O usuário da interação é obrigatório.");
        }
        this.comunicado = comunicado;
        this.idUsuario = idUsuario;
    }

    /** Preserva o instante da primeira abertura, que é o dado com valor de auditoria. */
    public void registrarVisualizacao() {
        if (this.visualizadoEm == null) {
            this.visualizadoEm = OffsetDateTime.now();
        }
    }

    /** "Não exibir novamente": some do mural deste usuário em definitivo. */
    public void dispensar() {
        registrarVisualizacao();
        if (this.dispensadoEm == null) {
            this.dispensadoEm = OffsetDateTime.now();
        }
        // Dispensar encerra o assunto; um adiamento pendente deixaria o comunicado
        // ressurgindo na data marcada, contrariando a escolha do usuário.
        this.adiadoAte = null;
    }

    /** "Ver depois": some até a data e volta sozinho. */
    public void adiarAte(OffsetDateTime momento) {
        if (!this.comunicado.permiteAdiar()) {
            throw new InvarianteVioladaException(
                    "Este comunicado exige confirmação de leitura e não pode ser adiado.");
        }
        if (momento == null || !momento.isAfter(OffsetDateTime.now())) {
            throw new InvarianteVioladaException("A data para rever o comunicado deve ser futura.");
        }
        registrarVisualizacao();
        this.adiadoAte = momento;
    }

    /**
     * "Li e entendi". Confirmar implica dispensar: exigir um segundo clique para
     * tirar da tela algo que a pessoa acabou de declarar ter lido é atrito puro.
     */
    public void confirmar() {
        if (!this.comunicado.isExigeConfirmacao()) {
            throw new InvarianteVioladaException(
                    "Este comunicado não exige confirmação de leitura.");
        }
        if (this.confirmadoEm == null) {
            this.confirmadoEm = OffsetDateTime.now();
        }
        dispensar();
    }

    public boolean isDispensado() {
        return this.dispensadoEm != null;
    }

    public boolean isConfirmado() {
        return this.confirmadoEm != null;
    }
}
