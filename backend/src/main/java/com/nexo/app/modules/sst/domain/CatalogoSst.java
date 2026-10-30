package com.nexo.app.modules.sst.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Entrada de um dos vocabulários de SST mantidos pelo usuário — classificação do
 * evento, local da lesão, natureza da lesão ou agente causador.
 *
 * <p>O tipo é imutável depois de criado. Trocá-lo reescreveria o significado do dado
 * em todo evento que já aponta para esta entrada: a "Queda de mesmo nível" que era a
 * classificação de trinta acidentes viraria, retroativamente, um local de lesão.</p>
 *
 * <p>A unicidade do nome é verificada sobre {@code nomeNormalizado} (sem acento, em
 * minúsculas) e apenas entre as entradas ativas — "Choque Elétrico" e "choque eletrico"
 * são o mesmo item para quem preenche o formulário, e uma entrada desativada não
 * disputa o nome com uma nova.</p>
 */
@Entity
@Table(name = "catalogos_sst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogoSst extends BaseEntity {

    private static final int NOME_MINIMO = 2;
    private static final int NOME_MAXIMO = 120;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, updatable = false, length = 30)
    private TipoCatalogoSst tipo;

    @Column(name = "nome", nullable = false, length = NOME_MAXIMO)
    private String nome;

    /** Nome sem acento e em minúsculas: é sobre ele que a unicidade e a busca comparam. */
    @Column(name = "nome_normalizado", nullable = false, length = NOME_MAXIMO)
    private String nomeNormalizado;

    @Column(name = "descricao", length = 255)
    private String descricao;

    /** Código da tabela externa correspondente (NBR 14280, eSocial), quando houver. */
    @Column(name = "codigo", length = 30)
    private String codigo;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    public CatalogoSst(TipoCatalogoSst tipo, String nome, String descricao, String codigo, Integer ordem) {
        if (tipo == null) {
            throw new InvarianteVioladaException("O tipo do catálogo é obrigatório.");
        }
        this.tipo = tipo;
        aplicarDados(nome, descricao, codigo, ordem);
    }

    /**
     * Altera nome, descrição, código e ordem. O tipo não entra: uma entrada nasce e
     * morre no mesmo vocabulário.
     */
    public void atualizar(String nome, String descricao, String codigo, Integer ordem) {
        exigirAtivo();
        aplicarDados(nome, descricao, codigo, ordem);
    }

    /** Indica se a entrada pertence ao vocabulário informado — a garantia que a FK não dá. */
    public boolean isDoTipo(TipoCatalogoSst tipoEsperado) {
        return this.tipo == tipoEsperado;
    }

    private void aplicarDados(String nome, String descricao, String codigo, Integer ordem) {
        if (nome == null || nome.isBlank()) {
            throw new InvarianteVioladaException("O nome da entrada do catálogo é obrigatório.");
        }
        String nomeLimpo = nome.trim();
        if (nomeLimpo.length() < NOME_MINIMO || nomeLimpo.length() > NOME_MAXIMO) {
            throw new InvarianteVioladaException(
                    "O nome da entrada do catálogo deve ter entre " + NOME_MINIMO + " e " + NOME_MAXIMO + " caracteres.");
        }
        if (ordem != null && ordem < 0) {
            throw new InvarianteVioladaException("A ordem de exibição não pode ser negativa.");
        }

        this.nome = nomeLimpo;
        this.nomeNormalizado = SpecificationUtils.normalizarTermo(nomeLimpo);
        this.descricao = textoOuNulo(descricao);
        this.codigo = textoOuNulo(codigo);
        this.ordem = ordem == null ? 0 : ordem;
    }

    private void exigirAtivo() {
        if (!isAtivo()) {
            throw new InvarianteVioladaException(
                    "Não é possível alterar uma entrada desativada. Reative-a antes de editá-la.");
        }
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
