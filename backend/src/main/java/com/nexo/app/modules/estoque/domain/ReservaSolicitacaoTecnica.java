package com.nexo.app.modules.estoque.domain;

import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "reservas_solicitacao_tecnica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservaSolicitacaoTecnica {

    private static final int MAXIMO_DIGITOS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_solicitacao", nullable = false, unique = true, updatable = false)
    private SolicitacaoMaterial solicitacao;

    @Column(name = "numero", nullable = false, precision = 20, scale = 0)
    private BigInteger numero;

    @Column(name = "data_criacao", nullable = false)
    private LocalDate dataCriacao;

    @Column(name = "id_agente_responsavel")
    private UUID idAgenteResponsavel;

    @Column(name = "nome_agente_snapshot", length = 200)
    private String nomeAgenteSnapshot;

    @Column(name = "empresa_agente_snapshot", length = 200)
    private String empresaAgenteSnapshot;

    @Column(name = "registrada_em", nullable = false)
    private OffsetDateTime registradaEm;

    @Column(name = "id_usuario_registro", nullable = false)
    private UUID idUsuarioRegistro;

    @Column(name = "atualizada_em")
    private OffsetDateTime atualizadaEm;

    @Column(name = "id_usuario_atualizacao")
    private UUID idUsuarioAtualizacao;

    @Version
    @Column(name = "versao", nullable = false)
    private long versao;

    public ReservaSolicitacaoTecnica(
            SolicitacaoMaterial solicitacao,
            BigInteger numero,
            LocalDate dataCriacao,
            UUID idAgenteResponsavel,
            String nomeAgenteSnapshot,
            String empresaAgenteSnapshot,
            UUID idUsuarioRegistro
    ) {
        this.solicitacao = Objects.requireNonNull(solicitacao, "Solicitacao e obrigatoria");
        this.numero = validarNumero(numero);
        this.dataCriacao = Objects.requireNonNull(dataCriacao, "Data real de criacao da reserva e obrigatoria");
        this.idAgenteResponsavel = idAgenteResponsavel;
        this.nomeAgenteSnapshot = textoOpcional(nomeAgenteSnapshot);
        this.empresaAgenteSnapshot = textoOpcional(empresaAgenteSnapshot);
        this.idUsuarioRegistro = Objects.requireNonNull(idUsuarioRegistro, "Usuario de registro e obrigatorio");
        this.registradaEm = OffsetDateTime.now();
        solicitacao.associarReserva(this);
    }

    public String getNumeroApi() {
        return numero.toString();
    }

    public boolean possuiMesmosDados(
            BigInteger numero,
            LocalDate dataCriacao,
            UUID idAgenteResponsavel
    ) {
        return this.numero.equals(numero)
                && this.dataCriacao.equals(dataCriacao)
                && Objects.equals(this.idAgenteResponsavel, idAgenteResponsavel);
    }

    public void atualizar(
            BigInteger numero,
            LocalDate dataCriacao,
            UUID idAgenteResponsavel,
            String nomeAgenteSnapshot,
            String empresaAgenteSnapshot,
            UUID idUsuarioAtualizacao
    ) {
        if (solicitacao.possuiFatoOperacional()) {
            throw new InvarianteVioladaException(
                    "A reserva nao pode ser alterada depois do primeiro fato operacional.");
        }
        this.numero = validarNumero(numero);
        this.dataCriacao = Objects.requireNonNull(dataCriacao, "Data real de criacao da reserva e obrigatoria");
        this.idAgenteResponsavel = idAgenteResponsavel;
        this.nomeAgenteSnapshot = textoOpcional(nomeAgenteSnapshot);
        this.empresaAgenteSnapshot = textoOpcional(empresaAgenteSnapshot);
        this.idUsuarioAtualizacao = Objects.requireNonNull(
                idUsuarioAtualizacao, "Usuario de atualizacao e obrigatorio");
        this.atualizadaEm = OffsetDateTime.now();
    }

    private static BigInteger validarNumero(BigInteger numero) {
        if (numero == null || numero.signum() < 0 || numero.toString().length() > MAXIMO_DIGITOS) {
            throw new InvarianteVioladaException("Numero da reserva deve conter ate 20 digitos numericos.");
        }
        return numero;
    }

    private static String textoObrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        return valor.trim();
    }

    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
