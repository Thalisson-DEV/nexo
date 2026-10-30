package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Modalidade ou classificação de uma inspeção de segurança operacional (ex.: Campo,
 * Alojamento, Câmera, Veicular).
 */
@Entity
@Table(name = "tipos_inspecao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoInspecao extends BaseEntity {

    private static final int NOME_MINIMO = 2;
    private static final int NOME_MAXIMO = 120;

    @Column(name = "nome", nullable = false, length = NOME_MAXIMO)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = NOME_MAXIMO)
    private String nomeNormalizado;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    public TipoInspecao(String nome, String descricao, Integer ordem) {
        aplicarDados(nome, descricao, ordem);
    }

    public void atualizar(String nome, String descricao, Integer ordem) {
        exigirAtiva();
        aplicarDados(nome, descricao, ordem);
    }

    private void aplicarDados(String nome, String descricao, Integer ordem) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("O nome do tipo de inspeção é obrigatório.");
        }
        String nomeLimpo = nome.trim();
        if (nomeLimpo.length() < NOME_MINIMO || nomeLimpo.length() > NOME_MAXIMO) {
            throw new InvarianteVioladaException(
                    "O nome do tipo de inspeção deve ter entre " + NOME_MINIMO + " e " + NOME_MAXIMO + " caracteres.");
        }
        this.nome = nomeLimpo;
        this.nomeNormalizado = SpecificationUtils.normalizarTermo(nomeLimpo);
        this.descricao = descricao == null || descricao.isBlank() ? null : descricao.trim();
        this.ordem = ordem != null && ordem >= 0 ? ordem : 0;
    }

    private void exigirAtiva() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException(
                    "Não é possível alterar um tipo de inspeção desativado. Reative-o antes de editá-lo.");
        }
    }
}
