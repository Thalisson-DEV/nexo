package com.nexo.app.modules.sst.domain;

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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Raiz do agregado de template de inspeção. Define um formulário "cru" composto
 * por grupos de perguntas, cada qual com suas perguntas e opções de resposta.
 *
 * <p>A estrutura (grupos/perguntas/opções) só pode ser alterada enquanto o
 * formulário ainda não foi utilizado em nenhuma inspeção lançada — essa trava é
 * garantida na camada de aplicação, pois o uso pertence a outro agregado.</p>
 */
@Entity
@Table(name = "formularios_inspecao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FormularioInspecao extends BaseEntity {

    @NotBlank
    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @JsonManagedReference
    @OrderBy("ordem ASC")
    @OneToMany(mappedBy = "formulario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GrupoPergunta> grupos = new ArrayList<>();

    public FormularioInspecao(String titulo, String descricao) {
        this.titulo = exigirTitulo(titulo);
        this.descricao = descricao;
    }

    /**
     * Atualiza os metadados do formulário (não afeta a estrutura de grupos/perguntas).
     */
    public void atualizarDados(String titulo, String descricao) {
        this.titulo = exigirTitulo(titulo);
        this.descricao = descricao;
    }

    /**
     * Adiciona um novo grupo ao final do formulário, atribuindo a próxima ordem.
     */
    public GrupoPergunta adicionarGrupo(String titulo) {
        GrupoPergunta grupo = new GrupoPergunta(this, titulo, grupos.size());
        this.grupos.add(grupo);
        return grupo;
    }

    /**
     * Garante que o formulário é consistente o suficiente para ser publicado/usado:
     * possui ao menos uma pergunta e toda pergunta objetiva possui opções.
     */
    public void validarEstrutura() {
        List<Pergunta> perguntas = grupos.stream()
                .flatMap(g -> g.getPerguntas().stream())
                .toList();

        if (perguntas.isEmpty()) {
            throw new InvarianteVioladaException("O formulário deve possuir ao menos uma pergunta.");
        }

        for (Pergunta pergunta : perguntas) {
            if (pergunta.getTipoResposta().permiteOpcoes() && pergunta.getOpcoes().isEmpty()) {
                throw new InvarianteVioladaException(
                        "A pergunta objetiva '" + pergunta.getEnunciado() + "' deve possuir ao menos uma opção de resposta.");
            }
            long totalPadrao = pergunta.getOpcoes().stream().filter(OpcaoResposta::isPadrao).count();
            if (pergunta.getTipoResposta() == TipoResposta.MULTIPLA_ESCOLHA && totalPadrao > 1) {
                throw new InvarianteVioladaException(
                        "A pergunta de múltipla escolha '" + pergunta.getEnunciado() + "' não pode ter mais de uma opção padrão.");
            }
            boolean padraoComDesvio = pergunta.getOpcoes().stream().anyMatch(o -> o.isPadrao() && o.isGeraDesvio());
            if (padraoComDesvio) {
                throw new InvarianteVioladaException(
                        "A pergunta '" + pergunta.getEnunciado() + "' possui opção padrão configurada para gerar desvio.");
            }
        }
    }

    /**
     * Reordena os grupos do formulário conforme a lista de IDs informada. Os IDs
     * têm de ser exatamente os grupos existentes. Operação de apresentação que
     * preserva todos os IDs e referências (permitida mesmo após uso do formulário).
     */
    public void reordenarGrupos(List<UUID> idsNaOrdem) {
        ReordenacaoValidator.validarPermutacao(
                grupos.stream().map(GrupoPergunta::getId).toList(), idsNaOrdem, "grupos");

        Map<UUID, GrupoPergunta> porId = grupos.stream()
                .collect(Collectors.toMap(GrupoPergunta::getId, Function.identity()));
        for (int i = 0; i < idsNaOrdem.size(); i++) {
            porId.get(idsNaOrdem.get(i)).atualizarOrdem(i);
        }
        grupos.sort(Comparator.comparingInt(GrupoPergunta::getOrdem));
    }

    /**
     * Reordena as perguntas de um grupo conforme a lista de IDs informada.
     */
    public void reordenarPerguntas(UUID idGrupo, List<UUID> idsNaOrdem) {
        buscarGrupo(idGrupo).reordenarPerguntas(idsNaOrdem);
    }

    /**
     * Reordena as opções de uma pergunta de um grupo conforme a lista de IDs.
     */
    public void reordenarOpcoes(UUID idGrupo, UUID idPergunta, List<UUID> idsNaOrdem) {
        buscarGrupo(idGrupo).reordenarOpcoes(idPergunta, idsNaOrdem);
    }

    private GrupoPergunta buscarGrupo(UUID idGrupo) {
        return grupos.stream()
                .filter(g -> g.getId().equals(idGrupo))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("Grupo não encontrado neste formulário."));
    }

    private static String exigirTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new InvarianteVioladaException("O título do formulário é obrigatório.");
        }
        return titulo;
    }
}
