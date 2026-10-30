package com.nexo.app.modules.frota.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;
import com.nexo.app.shared.infrastructure.security.HashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Ficha de condutor de um colaborador: os dados da CNH que a frota acompanha.
 *
 * <p>O colaborador continua no RH. O número da CNH é dado pessoal e fica cifrado,
 * com blind index para impedir duas fichas com a mesma CNH.</p>
 */
@Entity
@Table(name = "frota_condutores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Condutor extends BaseEntity {

    @Column(name = "id_colaborador", nullable = false, unique = true, updatable = false)
    private UUID idColaborador;

    @Convert(converter = CryptoConverterImpl.class)
    @Column(name = "cnh_numero")
    private String cnhNumero; // 🔒 Cifrado no banco

    @Column(name = "cnh_numero_hash")
    private String cnhNumeroHash; // 🔍 Blind index

    @Enumerated(EnumType.STRING)
    @Column(name = "cnh_categoria", length = 2)
    private CategoriaCnh cnhCategoria;

    @Column(name = "cnh_validade")
    private LocalDate cnhValidade;

    @Column(name = "cnh_primeira_habilitacao")
    private LocalDate cnhPrimeiraHabilitacao;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    public Condutor(UUID idColaborador) {
        this.idColaborador = Objects.requireNonNull(idColaborador, "O colaborador é obrigatório");
    }

    public void registrarCnh(String numero, CategoriaCnh categoria, LocalDate validade,
                             LocalDate primeiraHabilitacao, String observacao) {
        String numeroLimpo = numero == null ? null : numero.replaceAll("\\D", "");
        if (numeroLimpo == null || numeroLimpo.length() != 11) {
            throw new InvarianteVioladaException("O número da CNH deve ter 11 dígitos.");
        }
        if (categoria == null) {
            throw new InvarianteVioladaException("Informe a categoria da CNH.");
        }
        if (validade == null) {
            throw new InvarianteVioladaException("Informe a validade da CNH.");
        }
        if (primeiraHabilitacao != null && primeiraHabilitacao.isAfter(LocalDate.now())) {
            throw new InvarianteVioladaException("A data da primeira habilitação não pode estar no futuro.");
        }
        if (primeiraHabilitacao != null && validade.isBefore(primeiraHabilitacao)) {
            throw new InvarianteVioladaException("A validade não pode ser anterior à primeira habilitação.");
        }
        this.cnhNumero = numeroLimpo;
        this.cnhNumeroHash = HashUtils.gerarBlindIndex(numeroLimpo);
        this.cnhCategoria = categoria;
        this.cnhValidade = validade;
        this.cnhPrimeiraHabilitacao = primeiraHabilitacao;
        this.observacao = Textos.opcional(observacao, 1000);
        if (!isAtivo()) {
            ativar();
        }
    }

    public boolean isCnhVencidaEm(LocalDate data) {
        return cnhValidade != null && cnhValidade.isBefore(data);
    }

    /** CNH vencida ou de categoria insuficiente impede assumir o veículo. */
    public void exigirHabilitadoPara(CategoriaCnh exigida, LocalDate data) {
        if (cnhValidade == null) {
            throw new InvarianteVioladaException("O condutor não tem CNH registrada.");
        }
        if (isCnhVencidaEm(data)) {
            throw new InvarianteVioladaException("A CNH do condutor está vencida desde " + cnhValidade + ".");
        }
        if (cnhCategoria != null && !cnhCategoria.habilita(exigida)) {
            throw new InvarianteVioladaException(
                    "A CNH categoria " + cnhCategoria + " não habilita veículos que exigem categoria " + exigida + ".");
        }
    }

    /** Número com só os quatro últimos dígitos visíveis, para telas e listas. */
    public String cnhNumeroMascarado() {
        if (cnhNumero == null || cnhNumero.length() < 4) return null;
        return "*******" + cnhNumero.substring(cnhNumero.length() - 4);
    }
}
