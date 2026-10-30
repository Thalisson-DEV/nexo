package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agrupamento visual de perguntas dentro de um {@link FormularioInspecao}
 * (ex.: "Kit SMC", "EPIs"). Facilita a apresentação no client.
 */
@Entity
@Table(name = "grupos_pergunta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrupoPergunta extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_formulario", nullable = false)
    private FormularioInspecao formulario;

    @NotBlank
    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @JsonManagedReference
    @OrderBy("ordem ASC")
    @OneToMany(mappedBy = "grupo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pergunta> perguntas = new ArrayList<>();

    GrupoPergunta(FormularioInspecao formulario, String titulo, int ordem) {
        this.formulario = Objects.requireNonNull(formulario, "Formulário é obrigatório");
        this.titulo = exigirTitulo(titulo);
        this.ordem = ordem;
    }

    /**
     * Adiciona uma pergunta ao final do grupo, atribuindo a próxima ordem.
     */
    public Pergunta adicionarPergunta(String enunciado, TipoResposta tipoResposta, boolean obrigatoria) {
        Pergunta pergunta = new Pergunta(this, enunciado, tipoResposta, obrigatoria, perguntas.size());
        this.perguntas.add(pergunta);
        return pergunta;
    }

    void atualizarOrdem(int ordem) {
        this.ordem = ordem;
    }

    /**
     * Reordena as perguntas do grupo conforme a lista de IDs informada (permutação
     * exata das perguntas existentes), preservando os IDs.
     */
    void reordenarPerguntas(List<UUID> idsNaOrdem) {
        ReordenacaoValidator.validarPermutacao(
                perguntas.stream().map(Pergunta::getId).toList(), idsNaOrdem, "perguntas");

        Map<UUID, Pergunta> porId = perguntas.stream()
                .collect(Collectors.toMap(Pergunta::getId, Function.identity()));
        for (int i = 0; i < idsNaOrdem.size(); i++) {
            porId.get(idsNaOrdem.get(i)).atualizarOrdem(i);
        }
        perguntas.sort(Comparator.comparingInt(Pergunta::getOrdem));
    }

    void reordenarOpcoes(UUID idPergunta, List<UUID> idsNaOrdem) {
        perguntas.stream()
                .filter(p -> p.getId().equals(idPergunta))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Pergunta não encontrada neste grupo."))
                .reordenarOpcoes(idsNaOrdem);
    }

    private static String exigirTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new InvarianteVioladaException("O título do grupo é obrigatório.");
        }
        return titulo;
    }
}
