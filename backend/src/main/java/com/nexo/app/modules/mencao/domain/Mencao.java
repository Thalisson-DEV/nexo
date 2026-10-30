package com.nexo.app.modules.mencao.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Registro de que alguém foi marcado em um texto.
 *
 * <p>Uma linha por pessoa marcada, e não uma por texto com uma lista dentro, porque
 * toda pergunta útil aqui é sobre a pessoa: "onde fui marcado", "quantas marcações
 * pendentes tenho". O agrupamento por texto continua disponível pelo
 * {@link #idOrigem}.</p>
 *
 * <p>A entidade é imutável depois de criada. Editar uma mensagem não desmarca
 * ninguém: a notificação já foi entregue, e apagar o rastro faria a caixa de quem foi
 * avisado apontar para um fato que o sistema diz não ter acontecido.</p>
 */
@Entity
@Table(name = "mencoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mencao extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, updatable = false, length = 40)
    private OrigemMencao origem;

    /** Texto onde a marcação foi escrita (ex.: a interação do chamado). */
    @Column(name = "id_origem", nullable = false, updatable = false)
    private UUID idOrigem;

    /** Registro a que o texto pertence (ex.: o chamado), para o link de volta. */
    @Column(name = "id_registro", nullable = false, updatable = false)
    private UUID idRegistro;

    @Column(name = "id_usuario_autor", nullable = false, updatable = false)
    private UUID idUsuarioAutor;

    @Column(name = "id_usuario_mencionado", nullable = false, updatable = false)
    private UUID idUsuarioMencionado;

    @Column(name = "data_mencao", nullable = false, updatable = false)
    private OffsetDateTime dataMencao;

    public Mencao(OrigemMencao origem, UUID idOrigem, UUID idRegistro,
                  UUID idUsuarioAutor, UUID idUsuarioMencionado) {
        if (origem == null) {
            throw new InvarianteVioladaException("A origem da marcação é obrigatória.");
        }
        this.origem = origem;
        this.idOrigem = exigir(idOrigem, "Não foi possível identificar o texto da marcação.");
        this.idRegistro = exigir(idRegistro, "Não foi possível identificar o registro da marcação.");
        this.idUsuarioAutor = exigir(idUsuarioAutor, "Não foi possível identificar quem marcou.");
        this.idUsuarioMencionado = exigir(idUsuarioMencionado, "Não foi possível identificar quem foi marcado.");
        if (this.idUsuarioAutor.equals(this.idUsuarioMencionado)) {
            throw new InvarianteVioladaException("Não é possível marcar a si mesmo.");
        }
        this.dataMencao = OffsetDateTime.now();
    }

    private static UUID exigir(UUID valor, String mensagem) {
        if (valor == null) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor;
    }
}
