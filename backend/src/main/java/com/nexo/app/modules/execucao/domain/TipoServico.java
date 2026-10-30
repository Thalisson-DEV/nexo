package com.nexo.app.modules.execucao.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Locale;

/**
 * Tipo de serviço de uma nota executada em campo (ex.: Ligação nova, Corte, Religação).
 *
 * <p>O código é a classificação da nota e pode se repetir entre tipos (Modificação,
 * Alteração e Enlace são todos {@code CM}); quem identifica o tipo é a descrição,
 * única entre os tipos ativos.</p>
 */
@Entity
@Table(name = "tipos_servico")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoServico extends BaseEntity {

    static final int CODIGO_MAXIMO = 10;
    static final int DESCRICAO_MINIMO = 2;
    static final int DESCRICAO_MAXIMO = 120;

    @Column(name = "codigo", nullable = false, length = CODIGO_MAXIMO)
    private String codigo;

    @Column(name = "descricao", nullable = false, length = DESCRICAO_MAXIMO)
    private String descricao;

    @Column(name = "descricao_normalizada", nullable = false, length = DESCRICAO_MAXIMO)
    private String descricaoNormalizada;

    public TipoServico(String codigo, String descricao) {
        aplicarDados(codigo, descricao);
    }

    public void atualizar(String codigo, String descricao) {
        if (!isAtivo()) {
            throw new InvarianteVioladaException(
                    "Não é possível alterar um tipo de serviço desativado. Reative-o antes de editá-lo.");
        }
        aplicarDados(codigo, descricao);
    }

    private void aplicarDados(String codigo, String descricao) {
        if (codigo == null || codigo.isBlank()) {
            throw new InvarianteVioladaException("O código do tipo de serviço é obrigatório.");
        }
        String codigoLimpo = codigo.trim().toUpperCase(Locale.ROOT);
        if (codigoLimpo.length() > CODIGO_MAXIMO) {
            throw new InvarianteVioladaException(
                    "O código do tipo de serviço pode ter no máximo " + CODIGO_MAXIMO + " caracteres.");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new InvarianteVioladaException("A descrição do tipo de serviço é obrigatória.");
        }
        String descricaoLimpa = descricao.trim().replaceAll("\s+", " ");
        if (descricaoLimpa.length() < DESCRICAO_MINIMO || descricaoLimpa.length() > DESCRICAO_MAXIMO) {
            throw new InvarianteVioladaException("A descrição do tipo de serviço deve ter entre "
                    + DESCRICAO_MINIMO + " e " + DESCRICAO_MAXIMO + " caracteres.");
        }
        this.codigo = codigoLimpo;
        this.descricao = descricaoLimpa;
        this.descricaoNormalizada = SpecificationUtils.normalizarTermo(descricaoLimpa);
    }
}
