package com.nexo.app.modules.sst.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.nexo.app.shared.domain.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/**
 * Opção marcada em uma {@link RespostaInspecao} de pergunta objetiva.
 */
@Entity
@Table(name = "respostas_opcoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RespostaOpcaoSelecionada extends BaseEntity {

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resposta", nullable = false)
    private RespostaInspecao resposta;

    @NotNull
    @Column(name = "id_opcao_resposta", nullable = false)
    private UUID idOpcaoResposta;

    RespostaOpcaoSelecionada(RespostaInspecao resposta, UUID idOpcaoResposta) {
        this.resposta = Objects.requireNonNull(resposta, "Resposta é obrigatória");
        this.idOpcaoResposta = Objects.requireNonNull(idOpcaoResposta, "ID da opção de resposta é obrigatório");
    }
}
