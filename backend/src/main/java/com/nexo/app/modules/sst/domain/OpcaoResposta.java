package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Opção de resposta possível para uma {@link Pergunta} objetiva.
 * Quando {@code geraDesvio} é verdadeiro, escolher esta opção em uma inspeção
 * configura uma não-conformidade (desvio de segurança).
 */
@Entity
@Table(name = "opcoes_resposta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpcaoResposta extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pergunta", nullable = false)
    private Pergunta pergunta;

    @NotBlank
    @Column(name = "texto", nullable = false, length = 255)
    private String texto;

    @Column(name = "gera_desvio", nullable = false)
    private boolean geraDesvio;

    /**
     * Gravidade pré-definida da não-conformidade gerada por esta opção. Só faz
     * sentido (e é obrigatória) quando {@code geraDesvio} é verdadeiro; nas demais
     * opções permanece nula.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gravidade", length = 20)
    private GravidadeDesvio gravidade;

    @Column(name = "padrao", nullable = false)
    private boolean padrao;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    OpcaoResposta(Pergunta pergunta, String texto, boolean geraDesvio, GravidadeDesvio gravidade, int ordem) {
        this(pergunta, texto, geraDesvio, gravidade, false, ordem);
    }

    OpcaoResposta(Pergunta pergunta, String texto, boolean geraDesvio, GravidadeDesvio gravidade, boolean padrao, int ordem) {
        this.pergunta = Objects.requireNonNull(pergunta, "Pergunta é obrigatória");
        this.texto = exigirTexto(texto);
        this.geraDesvio = geraDesvio;
        this.gravidade = geraDesvio ? exigirGravidade(gravidade) : null;
        if (padrao && geraDesvio) {
            throw new InvarianteVioladaException("A opção que gera desvio não pode ser configurada como padrão.");
        }
        this.padrao = padrao;
        this.ordem = ordem;
    }

    void atualizarOrdem(int ordem) {
        this.ordem = ordem;
    }

    private static String exigirTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new InvarianteVioladaException("O texto da opção de resposta é obrigatório.");
        }
        return texto;
    }

    private static GravidadeDesvio exigirGravidade(GravidadeDesvio gravidade) {
        if (gravidade == null) {
            throw new InvarianteVioladaException("A gravidade é obrigatória para opções que geram desvio.");
        }
        return gravidade;
    }
}
