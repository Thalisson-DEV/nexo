package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Ramo mínimo e imutável de uma obra, identificado por código normalizado. */
@Entity
@Table(name = "ocorrencias_obras", uniqueConstraints = @UniqueConstraint(
        name = "uq_ocorrencias_obras_obra_codigo", columnNames = {"id_obra", "codigo_normalizado"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OcorrenciaObra extends BaseEntity {

    public static final int TAMANHO_MAXIMO = 100;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_obra", nullable = false, updatable = false)
    private Obra obra;

    @Column(name = "codigo_normalizado", nullable = false, length = TAMANHO_MAXIMO, updatable = false)
    private String codigoNormalizado;

    public OcorrenciaObra(Obra obra, String codigo) {
        this.obra = Objects.requireNonNull(obra, "A obra da ocorrencia e obrigatoria");
        if (!obra.isAtivo()) {
            throw new InvarianteVioladaException("A ocorrencia somente pode pertencer a uma obra ativa.");
        }
        this.codigoNormalizado = normalizarCodigo(codigo);
    }

    public static String normalizarCodigo(String valor) {
        if (valor == null) {
            throw new InvarianteVioladaException("O codigo da ocorrencia e obrigatorio.");
        }
        valor.codePoints().forEach(codigo -> {
            if (Character.getType(codigo) == Character.CONTROL) {
                throw new InvarianteVioladaException(
                        "O codigo da ocorrencia nao pode conter caracteres de controle.");
            }
        });
        String nfkc = Normalizer.normalize(valor, Normalizer.Form.NFKC);
        StringBuilder canonico = new StringBuilder(nfkc.length());
        boolean espacoPendente = false;
        for (int indice = 0; indice < nfkc.length();) {
            int codigo = nfkc.codePointAt(indice);
            indice += Character.charCount(codigo);
            if (Character.isWhitespace(codigo) || Character.isSpaceChar(codigo)) {
                espacoPendente = canonico.length() > 0;
                continue;
            }
            if (espacoPendente) {
                canonico.append(' ');
                espacoPendente = false;
            }
            canonico.appendCodePoint(codigo);
        }
        String normalizado = canonico.toString().toUpperCase(Locale.ROOT);
        if (normalizado.isEmpty()) {
            throw new InvarianteVioladaException("O codigo da ocorrencia e obrigatorio.");
        }
        if (normalizado.length() > TAMANHO_MAXIMO) {
            throw new InvarianteVioladaException(
                    "O codigo da ocorrencia deve ter no maximo " + TAMANHO_MAXIMO + " caracteres.");
        }
        return normalizado;
    }
}
