package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Técnico de segurança responsável por um {@link EventoSst}.
 *
 * <p>O id do colaborador é o que permite filtrar "meus eventos" e ranquear por técnico;
 * o nome e a função ao lado são o retrato do momento do registro, para que o evento
 * continue legível depois que a pessoa mudar de função ou sair da empresa.</p>
 */
@Entity
@Table(name = "evento_sst_tecnicos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TecnicoSegurancaEvento extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento", nullable = false)
    private EventoSst evento;

    @NotNull
    @Column(name = "id_colaborador", nullable = false)
    private UUID idColaborador;

    /** 🔒 Cifrado: nome de pessoa não fica em claro em lugar nenhum do sistema. */
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "funcao", length = 120)
    private String funcao;

    TecnicoSegurancaEvento(EventoSst evento, SnapshotTecnico snapshot) {
        this.evento = Objects.requireNonNull(evento, "O evento é obrigatório");
        Objects.requireNonNull(snapshot, "Os dados do técnico são obrigatórios");

        if (snapshot.idColaborador() == null) {
            throw new InvarianteVioladaException("O técnico de segurança deve ser um colaborador cadastrado.");
        }
        if (snapshot.nome() == null || snapshot.nome().isBlank()) {
            throw new InvarianteVioladaException("O nome do técnico de segurança é obrigatório.");
        }

        this.idColaborador = snapshot.idColaborador();
        this.nome = snapshot.nome().trim();
        this.funcao = snapshot.funcao() == null || snapshot.funcao().isBlank() ? null : snapshot.funcao().trim();
    }
}
