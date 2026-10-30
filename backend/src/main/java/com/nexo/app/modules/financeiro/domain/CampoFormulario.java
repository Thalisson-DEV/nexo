package com.nexo.app.modules.financeiro.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A presença de um campo do sistema numa variante de formulário.
 *
 * <p>É deliberadamente magro. Tudo que define o <em>comportamento</em> do campo —
 * chave, tipo, fonte das opções, limites de preenchimento, condição de exibição —
 * vem do {@link CampoCanonico} e não pode ser alterado por configuração. O que
 * este registro guarda é só o que o negócio realmente precisa variar entre uma
 * variante e outra: <b>se o campo aparece, com que rótulo, em que ordem e se é
 * obrigatório</b>.</p>
 *
 * <p>A consequência prática é que não existe estado inconsistente possível: não há
 * como configurar um limite numérico num campo de texto, nem uma condição que
 * aponte para um campo ausente, nem uma chave que colida com outra.</p>
 */
@Entity
@Table(name = "financeiro_campo_formulario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampoFormulario extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formulario_id", nullable = false)
    private FormularioSolicitacao formulario;

    @Enumerated(EnumType.STRING)
    @Column(name = "campo_canonico", nullable = false, length = 40)
    private CampoCanonico campoCanonico;

    @Column(name = "rotulo", nullable = false, length = 150)
    private String rotulo;

    /** Texto de apoio exibido abaixo do campo. */
    @Column(name = "ajuda", length = 500)
    private String ajuda;

    @Column(name = "obrigatorio", nullable = false)
    private boolean obrigatorio;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    CampoFormulario(FormularioSolicitacao formulario, CampoCanonico campoCanonico, String rotulo,
                    String ajuda, boolean obrigatorio, int ordem) {
        this.formulario = Objects.requireNonNull(formulario, "O formulário do campo é obrigatório.");
        if (campoCanonico == null) {
            throw new InvarianteVioladaException("Informe qual campo do sistema será utilizado.");
        }
        campoCanonico.exigirCompatibilidadeCom(formulario.getTipoSolicitacao());

        this.campoCanonico = campoCanonico;
        this.ordem = ordem;
        atualizarApresentacao(rotulo, ajuda, obrigatorio);
    }

    /**
     * Altera os aspectos de apresentação. Rótulo em branco devolve o campo ao
     * rótulo padrão do sistema, em vez de recusar — é o que o cadastrador espera ao
     * limpar o campo.
     */
    void atualizarApresentacao(String rotulo, String ajuda, boolean obrigatorio) {
        this.rotulo = StringUtils.hasText(rotulo) ? rotulo.trim() : campoCanonico.getRotuloPadrao();
        this.ajuda = normalizar(ajuda);
        this.obrigatorio = obrigatorio;
    }

    void atualizarOrdem(int ordem) {
        this.ordem = ordem;
    }

    // ── Derivados do canônico ───────────────────────────────────────────────────

    /** Chave publicada ao frontend e usada no payload. */
    public String getChave() {
        return campoCanonico.getChave();
    }

    public TipoCampo getTipoCampo() {
        return campoCanonico.getTipoCampo();
    }

    public FonteReferencia getFonteReferencia() {
        return campoCanonico.getFonteReferencia();
    }

    /** Bloco sob o qual o campo é exibido; vem do catálogo, não da configuração. */
    public GrupoCampo getGrupo() {
        return campoCanonico.getGrupo();
    }

    /**
     * Exemplo exibido no campo vazio. Vem do catálogo: depende do que o campo é,
     * não da variante, e assim todo formulário nasce com dicas úteis sem que
     * alguém precise escrever 28 textos à mão.
     */
    public String getPlaceholder() {
        return campoCanonico.getPlaceholderPadrao();
    }

    public CampoCanonico.Restricoes getRestricoes() {
        return campoCanonico.getRestricoes();
    }

    public Optional<CampoCanonico.Condicao> getCondicao() {
        return campoCanonico.getCondicao();
    }

    /** Valores aceitos, derivados da enumeração que apoia o campo. */
    public Set<String> valoresAceitos() {
        return campoCanonico.valoresAceitos();
    }

    private static String normalizar(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
