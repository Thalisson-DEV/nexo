package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "sst_treinamentos_governanca")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TreinamentoGovernancaSst extends BaseEntity {

    private static final Pattern CODIGO = Pattern.compile("[A-Z0-9_]{3,80}");
    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

    @Column(name = "codigo", nullable = false, length = 80)
    private String codigo;

    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = 160)
    private String nomeNormalizado;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "aplica_cipa", nullable = false)
    private boolean aplicaCipa;

    @Column(name = "aplica_brigada", nullable = false)
    private boolean aplicaBrigada;

    public TreinamentoGovernancaSst(String codigo, String nome, String descricao,
                                    boolean aplicaCipa, boolean aplicaBrigada) {
        this.codigo = normalizarCodigo(codigo);
        atualizar(nome, descricao, aplicaCipa, aplicaBrigada);
    }

    public void atualizar(String nome, String descricao, boolean aplicaCipa, boolean aplicaBrigada) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException(
                    "Treinamento desativado deve ser reativado antes de ser alterado.");
        }
        this.nome = exigirTexto(nome, 160, "O nome do treinamento é obrigatório.");
        this.nomeNormalizado = normalizar(this.nome);
        this.descricao = limitar(descricao, 500);
        this.aplicaCipa = aplicaCipa;
        this.aplicaBrigada = aplicaBrigada;
    }

    public boolean aplicaA(TipoCorpoGovernancaSst tipoCorpo) {
        return tipoCorpo != null && switch (tipoCorpo) {
            case CIPA -> aplicaCipa;
            case BRIGADA -> aplicaBrigada;
        };
    }

    private static String normalizarCodigo(String valor) {
        String codigo = exigirTexto(valor, 80, "O código do treinamento é obrigatório.")
                .toUpperCase(Locale.ROOT);
        if (!CODIGO.matcher(codigo).matches()) {
            throw new InvarianteVioladaException(
                    "O código do treinamento deve usar apenas letras, números e sublinhado.");
        }
        return codigo;
    }

    public static String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        return DIACRITICOS.matcher(Normalizer.normalize(valor.trim().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD)).replaceAll("");
    }

    private static String exigirTexto(String valor, int limite, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new InvarianteVioladaException(mensagem);
        }
        return limitar(valor, limite);
    }

    private static String limitar(String valor, int limite) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > limite) {
            throw new InvarianteVioladaException("O valor excede " + limite + " caracteres.");
        }
        return limpo;
    }
}
