package com.nexo.app.modules.notificacao.domain;

import org.springframework.util.StringUtils;

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

/**
 * Um filtro sobre os dados do gatilho. Todas as condições de uma regra precisam ser
 * satisfeitas (E lógico) — quem quer alternativas cria duas regras, o que também
 * deixa os textos independentes.
 */
@Entity
@Table(name = "condicoes_regra_notificacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CondicaoRegra extends BaseEntity {

    @JsonBackReference("regra-condicoes")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_regra", nullable = false)
    private RegraNotificacao regra;

    @Column(name = "campo", nullable = false, length = 60)
    private String campo;

    @Enumerated(EnumType.STRING)
    @Column(name = "operador", nullable = false, length = 20)
    private OperadorCondicao operador;

    /** Sempre texto: a comparação decide o tipo em tempo de avaliação. */
    @Column(name = "valor", nullable = false, length = 500)
    private String valor;

    CondicaoRegra(RegraNotificacao regra, String campo, OperadorCondicao operador, String valor) {
        if (regra == null) {
            throw new InvarianteVioladaException("A regra da condição é obrigatória.");
        }
        if (!StringUtils.hasText(campo)) {
            throw new InvarianteVioladaException("Escolha o campo da condição.");
        }
        ComparadorCondicao.validar(operador, valor);

        this.regra = regra;
        this.campo = campo.trim();
        this.operador = operador;
        this.valor = valor.trim();
    }

    /**
     * Compara o valor configurado com o que veio do gatilho.
     *
     * <p>Números são comparados como números quando ambos os lados são numéricos;
     * caso contrário a comparação é textual e sem diferenciar maiúsculas. Isso evita
     * que "10" > "9" dê falso por ordem alfabética, que é o erro clássico quando o
     * contexto trafega como mapa de objetos.</p>
     */
    public boolean satisfeitaPor(Object valorDoContexto) {
        return ComparadorCondicao.satisfeitaPor(this.operador, this.valor, valorDoContexto);
    }
}
