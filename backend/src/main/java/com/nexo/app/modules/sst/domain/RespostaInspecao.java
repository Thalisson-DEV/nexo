package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resposta dada a uma pergunta dentro de uma {@link InspecaoSeguranca}.
 * Para perguntas objetivas guarda as opções selecionadas; para perguntas de
 * resposta livre guarda o valor digitado (texto, número ou data).
 */
@Entity
@Table(name = "respostas_inspecao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RespostaInspecao extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inspecao", nullable = false)
    private InspecaoSeguranca inspecao;

    @NotNull
    @Column(name = "id_pergunta", nullable = false)
    private UUID idPergunta;

    @Column(name = "valor_texto", length = 1000)
    private String valorTexto;

    @Column(name = "valor_numero", precision = 18, scale = 4)
    private BigDecimal valorNumero;

    @Column(name = "valor_data")
    private LocalDate valorData;

    @JsonManagedReference
    @OneToMany(mappedBy = "resposta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespostaOpcaoSelecionada> opcoesSelecionadas = new ArrayList<>();

    RespostaInspecao(InspecaoSeguranca inspecao, UUID idPergunta, String valorTexto,
                     BigDecimal valorNumero, LocalDate valorData) {
        this.inspecao = Objects.requireNonNull(inspecao, "Inspeção é obrigatória");
        this.idPergunta = Objects.requireNonNull(idPergunta, "ID da pergunta é obrigatório");
        this.valorTexto = valorTexto;
        this.valorNumero = valorNumero;
        this.valorData = valorData;
    }

    void selecionarOpcao(UUID idOpcaoResposta) {
        this.opcoesSelecionadas.add(new RespostaOpcaoSelecionada(this, idOpcaoResposta));
    }

    public void atualizar(List<UUID> novasOpcoes, String valorTexto, BigDecimal valorNumero, LocalDate valorData) {
        this.valorTexto = valorTexto;
        this.valorNumero = valorNumero;
        this.valorData = valorData;
        this.opcoesSelecionadas.clear();
        if (novasOpcoes != null) {
            novasOpcoes.forEach(this::selecionarOpcao);
        }
    }
}
