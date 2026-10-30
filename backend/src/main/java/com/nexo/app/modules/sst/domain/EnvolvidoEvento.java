package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Pessoa envolvida em um {@link EventoSst} — acidentada, testemunha ou participante.
 *
 * <p>Colaborador e terceiro compartilham a tabela porque compartilham o papel no
 * evento; o que muda é como cada um se identifica. O colaborador traz o id do RH (e é
 * por ele que se agrupa e se filtra). O terceiro não tem id: traz nome e empresa, e o
 * nome ganha um <b>blind index</b> para ser buscável apesar de cifrado.</p>
 *
 * <p>Daí a assimetria que a documentação da API declara: nome de colaborador casa
 * <b>parcialmente</b> (o RH resolve o trecho em ids, contra a coluna normalizada que só
 * ele enxerga), enquanto nome de terceiro casa de forma <b>exata</b> — um hash não tem
 * "começa com".</p>
 */
@Entity
@Table(name = "evento_sst_envolvidos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnvolvidoEvento extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_evento", nullable = false)
    private EventoSst evento;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoEnvolvido tipo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "papel", nullable = false, length = 20)
    private PapelEnvolvido papel;

    /** Apenas para {@link TipoEnvolvido#COLABORADOR}. Sem FK: a fronteira com o RH é a API pública. */
    @Column(name = "id_colaborador")
    private UUID idColaborador;

    /** 🔒 Cifrado, como no RH: copiar o nome não pode significar publicá-lo. */
    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "nome", nullable = false)
    private String nome;

    /** 🔍 Blind index do nome do terceiro — a única forma de procurá-lo sem decifrar a coluna. */
    @Column(name = "nome_hash")
    private String nomeHash;

    @Column(name = "funcao", length = 120)
    private String funcao;

    /** Base operacional do colaborador no momento do evento (texto, snapshot). */
    @Column(name = "base_operacional", length = 120)
    private String baseOperacional;

    /** Empresa do terceiro. Obrigatória para ele, ausente para colaborador. */
    @Column(name = "empresa", length = 150)
    private String empresa;

    EnvolvidoEvento(EventoSst evento, SnapshotEnvolvido snapshot) {
        this.evento = Objects.requireNonNull(evento, "O evento é obrigatório");
        Objects.requireNonNull(snapshot, "Os dados do envolvido são obrigatórios");

        if (snapshot.tipo() == null) {
            throw new InvarianteVioladaException("O tipo do envolvido é obrigatório (colaborador ou terceiro).");
        }
        if (snapshot.papel() == null) {
            throw new InvarianteVioladaException("O papel do envolvido é obrigatório.");
        }
        if (snapshot.nome() == null || snapshot.nome().isBlank()) {
            throw new InvarianteVioladaException("O nome do envolvido é obrigatório.");
        }

        this.tipo = snapshot.tipo();
        this.papel = snapshot.papel();
        this.nome = snapshot.nome().trim();
        this.funcao = textoOuNulo(snapshot.funcao());

        if (snapshot.tipo() == TipoEnvolvido.COLABORADOR) {
            aplicarColaborador(snapshot);
        } else {
            aplicarTerceiro(snapshot);
        }
    }

    /** Foi esta pessoa quem se lesionou? É o que os tipos com lesão exigem que exista. */
    public boolean isAcidentado() {
        return papel == PapelEnvolvido.ACIDENTADO;
    }

    private void aplicarColaborador(SnapshotEnvolvido snapshot) {
        if (snapshot.idColaborador() == null) {
            throw new InvarianteVioladaException(
                    "Envolvido do tipo COLABORADOR exige o colaborador cadastrado. "
                            + "Se a pessoa não tem cadastro, registre-a como TERCEIRO.");
        }
        this.idColaborador = snapshot.idColaborador();
        this.baseOperacional = textoOuNulo(snapshot.baseOperacional());
        this.empresa = null;
        // Sem blind index: o filtro por nome de colaborador é resolvido em ids no RH,
        // que é quem tem a coluna de busca normalizada.
        this.nomeHash = null;
    }

    private void aplicarTerceiro(SnapshotEnvolvido snapshot) {
        if (snapshot.idColaborador() != null) {
            throw new InvarianteVioladaException(
                    "Envolvido do tipo TERCEIRO não pode apontar para um colaborador cadastrado.");
        }
        if (snapshot.empresa() == null || snapshot.empresa().isBlank()) {
            throw new InvarianteVioladaException(
                    "A empresa do terceiro é obrigatória: sem cadastro, é ela que identifica a pessoa.");
        }
        this.idColaborador = null;
        this.baseOperacional = null;
        this.empresa = snapshot.empresa().trim();
        this.nomeHash = HashUtils.gerarBlindIndex(this.nome.toUpperCase());
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
